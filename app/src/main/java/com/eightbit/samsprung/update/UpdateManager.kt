/*
 * ====================================================================
 * Copyright (c) 2021-2023 AbandonedCart.  All rights reserved.
 *
 * See https://github.com/SamSprung/.github/blob/main/LICENSE#L5
 * ====================================================================
 *
 * The license and distribution terms for any publicly available version or
 * derivative of this code cannot be changed.  i.e. this code cannot simply be
 * copied and put under another distribution license
 * [including the GNU Public License.] Content not subject to these terms is
 * subject to to the terms and conditions of the Apache License, Version 2.0.
 */

package com.eightbit.samsprung.update

import android.app.Activity
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.eightbit.net.RequestGitHubAPI
import com.eightbit.samsprung.BuildConfig
import com.eightbit.samsprung.R
import com.eightbit.samsprung.SamSprung
import com.eightbit.samsprung.settings.CoverPreferences
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.UpdateAvailability
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.*

class UpdateManager(private var activity: Activity) {

    private val repo = "https://api.github.com/repos/SamSprung/SamSprung-TooUI/releases/tags/"
    var listenerGit: GitUpdateListener? = null
    var listenerPlay: PlayUpdateListener? = null
    private var appUpdateManager: AppUpdateManager? = null
    private var isUpdateAvailable = false

    private val scopeIO = CoroutineScope(Dispatchers.IO)

    init {
        if (BuildConfig.GOOGLE_PLAY) configureManager() else configureUpdates()
    }

    private fun configureManager() {
        if (null == appUpdateManager) appUpdateManager = AppUpdateManagerFactory.create(activity)
        val appUpdateInfoTask = appUpdateManager?.appUpdateInfo
        // Checks that the platform will allow the specified type of update.
        appUpdateInfoTask?.addOnSuccessListener { appUpdateInfo ->
            isUpdateAvailable = appUpdateInfo.updateAvailability() == UpdateAvailability
                .UPDATE_AVAILABLE && appUpdateInfo.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
            if (isUpdateAvailable && null != listenerPlay)
                listenerPlay?.onPlayUpdateFound(appUpdateInfo)
        }
    }

    private fun configureUpdates() {
        try {
            (activity.getSystemService(AppCompatActivity.NOTIFICATION_SERVICE)
                    as NotificationManager).cancel(SamSprung.request_code)
        } catch (ignored: Exception) { }
        if (activity is UpdateShimActivity) {
            activity.applicationContext.packageManager.packageInstaller.run {
                mySessions.forEach {
                    try {
                        abandonSession(it.sessionId)
                    } catch (ignored: Exception) { }
                }
            }
        } else {
            scopeIO.launch(Dispatchers.IO) {
                activity.externalCacheDir?.listFiles { _, name ->
                    name.lowercase(Locale.getDefault()).endsWith(".apk")
                }?.forEach { if (!it.isDirectory) it.delete() }
            }
            retrieveUpdate()
        }
    }

    private fun installUpdate(apkUri: Uri) {
        scopeIO.launch(Dispatchers.IO) {
            activity.run {
                applicationContext.contentResolver.openInputStream(apkUri)?.use { apkStream ->
                    val length = DocumentFile.fromSingleUri(
                        applicationContext, apkUri)?.length() ?: -1
                    val session = applicationContext.packageManager.packageInstaller.run {
                        val params = PackageInstaller.SessionParams(
                            PackageInstaller.SessionParams.MODE_FULL_INSTALL
                        )
                        openSession(createSession(params))
                    }
                    session.openWrite("NAME", 0, length).use { sessionStream ->
                        apkStream.copyTo(sessionStream)
                        session.fsync(sessionStream)
                    }
                    val pi = PendingIntent.getBroadcast(
                        applicationContext, SamSprung.request_code,
                        Intent(applicationContext, UpdateReceiver::class.java)
                            .setAction(SamSprung.updating),
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                        else PendingIntent.FLAG_UPDATE_CURRENT
                    )
                    session.commit(pi.intentSender)
                    session.close()
                }
            }
        }
    }

    fun downloadUpdate(link: String) {
        if (activity.packageManager.canRequestPackageInstalls()) {
            val download: String = link.substring(link.lastIndexOf(File.separator) + 1)
            val apk = File(activity.externalCacheDir, download)
            scopeIO.launch(Dispatchers.IO) {
                URL(link).openStream().use { stream ->
                    FileOutputStream(apk).use {
                        stream.copyTo(it)
                        installUpdate(FileProvider.getUriForFile(
                            activity.applicationContext, SamSprung.provider, apk
                        ))
                    }
                }
            }
        } else if (activity is CoverPreferences) {
            (activity as CoverPreferences).updateLauncher.launch(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).setData(
                    Uri.parse(String.format("package:%s", activity.packageName))))
        }
    }

    fun downloadPlayUpdate(appUpdateInfo: AppUpdateInfo) {
        appUpdateManager?.startUpdateFlowForResult(
            // Pass the intent that is returned by 'getAppUpdateInfo()'.
            appUpdateInfo,
            // Or 'AppUpdateType.FLEXIBLE' for flexible updates.
            AppUpdateType.IMMEDIATE,
            // The current activity making the update request.
            activity,
            // Include a request code to later monitor this update request.
            SamSprung.request_code)

    }

    private fun parseUpdateJSON(result: String) {
        try {
            val jsonObject = JSONTokener(result).nextValue() as JSONObject
            val lastCommit = (jsonObject["name"] as String).substring(
                activity.getString(R.string.samsprung).length + 1
            )
            val assets = jsonObject["assets"] as JSONArray
            val asset = assets[0] as JSONObject
            val downloadUrl = asset["browser_download_url"] as String
            isUpdateAvailable = BuildConfig.COMMIT != lastCommit
            if (isUpdateAvailable && null != listenerGit)
                listenerGit?.onUpdateFound(downloadUrl)
        } catch (ignored: JSONException) { }
    }

    fun retrieveUpdate() {
        RequestGitHubAPI("${repo}sideload")
            .setResultListener(object : RequestGitHubAPI.ResultListener {
            override fun onResults(result: String) {
                parseUpdateJSON(result)
            }
        })
    }

    fun hasPendingUpdate(): Boolean {
        return isUpdateAvailable
    }

    fun setUpdateListener(listener: GitUpdateListener) {
        this.listenerGit = listener
    }

    fun setPlayUpdateListener(listenerPlay: PlayUpdateListener) {
        this.listenerPlay = listenerPlay
    }

    interface GitUpdateListener {
        fun onUpdateFound(downloadUrl: String)
    }

    interface PlayUpdateListener {
        fun onPlayUpdateFound(appUpdateInfo: AppUpdateInfo)
    }
}