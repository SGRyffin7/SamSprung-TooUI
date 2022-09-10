package com.eightbit.samsprung.settings

/* ====================================================================
 * Copyright (c) 2012-2022 AbandonedCart.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. All advertising materials mentioning features or use of this
 *    software and redistributions of any form whatsoever
 *    must display the following acknowledgment:
 *    "This product includes software developed by AbandonedCart" unless
 *    otherwise displayed by tagged, public repository entries.
 *
 * 4. The names "8-Bit Dream", "TwistedUmbrella" and "AbandonedCart"
 *    must not be used in any form to endorse or promote products
 *    derived from this software without prior written permission. For
 *    written permission, please contact enderinexiledc@gmail.com
 *
 * 5. Products derived from this software may not be called "8-Bit Dream",
 *    "TwistedUmbrella" or "AbandonedCart" nor may these labels appear
 *    in their names without prior written permission of AbandonedCart.
 *
 * THIS SOFTWARE IS PROVIDED BY AbandonedCart ``AS IS'' AND ANY
 * EXPRESSED OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE OpenSSL PROJECT OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 * ====================================================================
 *
 * The license and distribution terms for any publicly available version or
 * derivative of this code cannot be changed.  i.e. this code cannot simply be
 * copied and put under another distribution license
 * [including the GNU Public License.] Content not subject to these terms is
 * subject to to the terms and conditions of the Apache License, Version 2.0.
 */

import android.Manifest
import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.app.Dialog
import android.app.KeyguardManager
import android.app.WallpaperManager
import android.content.*
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Resources
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.drawable.ColorDrawable
import android.icu.text.DecimalFormatSymbols
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.provider.Settings
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.RelativeSizeSpan
import android.util.TypedValue
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.TranslateAnimation
import android.webkit.MimeTypeMap
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ContextThemeWrapper
import androidx.appcompat.widget.AppCompatImageView
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.widget.Toolbar
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import androidx.core.view.GravityCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.android.billingclient.api.*
import com.eightbit.content.ScaledContext
import com.eightbit.io.Debug
import com.eightbit.material.IconifiedSnackbar
import com.eightbit.pm.PackageRetriever
import com.eightbit.samsprung.*
import com.eightbit.view.AnimatedLinearLayout
import com.eightbitlab.blurview.BlurView
import com.eightbitlab.blurview.RenderScriptBlur
import com.google.android.material.snackbar.Snackbar
import com.google.android.play.core.appupdate.AppUpdateInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import myinnos.indexfastscrollrecycler.IndexFastScrollRecyclerView
import java.io.*
import java.util.concurrent.Executors
import java.util.regex.Pattern
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


class CoverPreferences : AppCompatActivity() {

    private val CharSequence.toPref get() = this.toString()
        .lowercase().replace(" ", "_")

    private val Number.toPx get() = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, this.toFloat(),
        Resources.getSystem().displayMetrics
    )

    private lateinit var prefs: SharedPreferences
    private lateinit var coordinator: CoordinatorLayout
    private var updateCheck : CheckUpdatesTask? = null

    private var hasPremiumSupport = false
    private lateinit var mainSwitch: SwitchCompat
    private lateinit var accessibility: SwitchCompat
    private lateinit var optimization: SwitchCompat
    private lateinit var notifications: SwitchCompat
    private lateinit var statistics: SwitchCompat
    private lateinit var keyboard: SwitchCompat
    private lateinit var wikiDrawer: DrawerLayout

    private lateinit var hiddenList: IndexFastScrollRecyclerView

    private lateinit var billingClient: BillingClient
    private val iapSkuDetails = ArrayList<ProductDetails>()
    private val subSkuDetails = ArrayList<ProductDetails>()

    @SuppressLint("ClickableViewAccessibility", "InflateParams")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        prefs = getSharedPreferences(SamSprung.prefsValue, MODE_PRIVATE)
        setTheme(R.style.Theme_SecondScreen)
        setContentView(R.layout.preferences_layout)

        setLoadCompleted()

        val componentName = ComponentName(applicationContext, NotificationReceiver::class.java)
        packageManager.setComponentEnabledSetting(componentName,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP
        )
        packageManager.setComponentEnabledSetting(componentName,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP
        )

        getBillingConnection()

        coordinator = findViewById(R.id.coordinator)
        findViewById<BlurView>(R.id.blurContainer).setupWith(coordinator)
            .setFrameClearDrawable(coordinator.background)
            .setBlurRadius(10f).setBlurAutoUpdate(true)
            .setHasFixedTransformationMatrix(false)
            .setBlurAlgorithm(RenderScriptBlur(this))

        wikiDrawer = findViewById(R.id.drawer_layout)
        findViewById<TextView>(R.id.build_info).text = (getString(R.string.build_hash_short, BuildConfig.COMMIT))
        findViewById<LinearLayout>(R.id.build_layout).setOnClickListener {
            wikiDrawer.openDrawer(GravityCompat.START)
        }

        initializeLayout()

        val googlePlay = findViewById<LinearLayout>(R.id.button_donate)
        googlePlay.setOnClickListener { onSendDonationClicked() }

        findViewById<LinearLayout>(R.id.logcat).setOnClickListener {
            if (updateCheck?.hasPendingUpdate() == true) {
                IconifiedSnackbar(this).buildTickerBar(
                    getString(R.string.update_service, getString(R.string.app_name))
                ).show()
                return@setOnClickListener
            }
            if (!Debug(this).captureLogcat(isDeviceSecure())) {
                wikiDrawer.openDrawer(GravityCompat.START)
            }
        }

        notifications = findViewById(R.id.notifications_switch)
        notifications.isChecked = hasNotificationListener()
        findViewById<LinearLayout>(R.id.notifications).setOnClickListener {
            notificationLauncher.launch(Intent(
                Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
            ))
        }

        statistics = findViewById(R.id.usage_switch)
        statistics.isChecked = hasUsageStatistics()
        findViewById<LinearLayout>(R.id.usage_layout).setOnClickListener {
            usageLauncher.launch(Intent(
                Settings.ACTION_USAGE_ACCESS_SETTINGS
            ))
        }

        optimization = findViewById(R.id.optimization_switch)
        optimization.isChecked = ignoreBatteryOptimization()
        findViewById<LinearLayout>(R.id.optimization).setOnClickListener {
            optimizationLauncher.launch(Intent(
                Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
            ))
        }

        accessibility = findViewById(R.id.accessibility_switch)
        accessibility.isChecked = hasAccessibility()
        findViewById<LinearLayout>(R.id.accessibility).setOnClickListener {
            if (SamSprung.isGooglePlay() && !accessibility.isChecked) {
                AlertDialog.Builder(this)
                    .setMessage(getString(R.string.accessibility_disclaimer))
                    .setPositiveButton(R.string.button_confirm) { dialog, _ ->
                        accessibilityLauncher.launch(Intent(
                            Settings.ACTION_ACCESSIBILITY_SETTINGS
                        ))
                        dialog.dismiss()
                    }
                    .setNegativeButton(R.string.button_cancel) { dialog, _ ->
                        accessibility.isChecked = false
                        dialog.dismiss()
                    }.show()
            } else {
                accessibilityLauncher.launch(Intent(
                    Settings.ACTION_ACCESSIBILITY_SETTINGS
                ))
            }
        }

        findViewById<LinearLayout>(R.id.voice_layout).setOnClickListener {
            requestVoice.launch(Manifest.permission.RECORD_AUDIO)
        }
        toggleVoiceIcon(hasPermission(Manifest.permission.RECORD_AUDIO))

        keyboard = findViewById(R.id.keyboard_switch)
        keyboard.isClickable = false
        keyboard.isChecked = hasKeyboardInstalled()
        findViewById<LinearLayout>(R.id.keyboard_layout).setOnClickListener {
            try {
                keyboardLauncher.launch(Intent(Intent.ACTION_VIEW, Uri.parse(
                    "market://details?id=" + BuildConfig.APPLICATION_ID + ".ime"
                )))
            } catch (exception: ActivityNotFoundException) {
                keyboardLauncher.launch(Intent(Intent.ACTION_VIEW, Uri.parse(
                        "https://play.google.com/store/apps/details?id="
                                + BuildConfig.APPLICATION_ID + ".ime"
                )))
            }
        }

        val nestedOptions = findViewById<ScrollView>(R.id.nested_options)
        val general = findViewById<LinearLayout>(R.id.general)
        val drawer = findViewById<LinearLayout>(R.id.drawer)
        val notices = findViewById<LinearLayout>(R.id.notices)

        findViewById<LinearLayout>(R.id.menu_general).setOnClickListener {
            if (general.isGone && (drawer.isVisible || notices.isVisible)) {
                drawer.isGone = true
                notices.isGone = true
                general.postDelayed({
                    general.isVisible = true
                    nestedOptions.scrollToDescendant(general)
                }, 100)
            } else {
                general.isGone = general.isVisible
                nestedOptions.scrollToDescendant(general)
            }
        }
        general.isGone = true

        val color = prefs.getInt(SamSprung.prefColors, Color.rgb(255, 255, 255))

        val textRed = findViewById<TextView>(R.id.color_red_text)
        val colorRedBar = findViewById<SeekBar>(R.id.color_red_bar)
        colorRedBar.progress = color.red

        colorRedBar.progressTintList = ColorStateList
            .valueOf(Color.rgb(colorRedBar.progress, 0,0))

        val textGreen = findViewById<TextView>(R.id.color_green_text)
        val colorGreenBar = findViewById<SeekBar>(R.id.color_green_bar)
        colorGreenBar.progress = color.green

        colorGreenBar.progressTintList = ColorStateList
            .valueOf(Color.rgb(0, colorGreenBar.progress, 0))

        val textBlue = findViewById<TextView>(R.id.color_blue_text)
        val colorBlueBar = findViewById<SeekBar>(R.id.color_blue_bar)
        colorBlueBar.progress = color.blue

        colorBlueBar.progressTintList = ColorStateList
            .valueOf(Color.rgb(0, 0, colorBlueBar.progress))

        val alphaFloat = prefs.getFloat(SamSprung.prefAlphas, 1f)
        val alphaPreview = findViewById<View>(R.id.alpha_preview)
        val alphaView = findViewById<LinearLayout>(R.id.color_alpha_view)
        val colorAlphaBar = findViewById<SeekBar>(R.id.color_alpha_bar)
        alphaPreview.setBackgroundColor(color)
        alphaPreview.alpha = alphaFloat
        colorAlphaBar.progress = (alphaFloat * 100).toInt()

        val colorPanel = findViewById<AnimatedLinearLayout>(R.id.color_panel)
        val colorComposite = findViewById<View>(R.id.color_composite)
        colorComposite.setBackgroundColor(color)

        colorComposite.setOnClickListener {
            if (colorPanel.isVisible) {
                val animate = TranslateAnimation(
                    0f, 0f, 0f, -colorPanel.height.toFloat()
                )
                animate.duration = 750
                animate.fillAfter = false
                colorPanel.setAnimationListener(object : AnimatedLinearLayout.AnimationListener {
                    override fun onAnimationStart(layout: AnimatedLinearLayout) {
                        colorPanel.postDelayed({
                            textRed.visibility = View.INVISIBLE
                        }, 125)
                        colorPanel.postDelayed({
                            colorRedBar.visibility = View.INVISIBLE
                        }, 150)
                        colorPanel.postDelayed({
                            textGreen.visibility = View.INVISIBLE
                        }, 250)
                        colorPanel.postDelayed({
                            colorGreenBar.visibility = View.INVISIBLE
                        }, 275)
                        colorPanel.postDelayed({
                            textBlue.visibility = View.INVISIBLE
                        }, 400)
                        colorPanel.postDelayed({
                            colorBlueBar.visibility = View.INVISIBLE
                        }, 425)
                        colorPanel.postDelayed({
                            alphaView.visibility = View.INVISIBLE
                        }, 525)
                        colorPanel.postDelayed({
                            colorAlphaBar.visibility = View.INVISIBLE
                        }, 550)
                    }
                    override fun onAnimationEnd(layout: AnimatedLinearLayout) {
                        colorPanel.clearAnimation()
                        layout.setAnimationListener(null)
                        textRed.visibility = View.GONE
                        colorRedBar.visibility = View.GONE
                        textGreen.visibility = View.GONE
                        colorGreenBar.visibility = View.GONE
                        textBlue.visibility = View.GONE
                        colorBlueBar.visibility = View.GONE
                        alphaView.visibility = View.GONE
                        colorAlphaBar.visibility = View.GONE
                        colorPanel.visibility = View.GONE
                    }
                })
                colorPanel.startAnimation(animate)
            } else {
                colorPanel.visibility = View.VISIBLE
                colorPanel.postDelayed({
                    colorAlphaBar.visibility = View.VISIBLE
                }, 50)
                colorPanel.postDelayed({
                    alphaView.visibility = View.VISIBLE
                }, 75)
                colorPanel.postDelayed({
                    colorBlueBar.visibility = View.VISIBLE
                }, 125)
                colorPanel.postDelayed({
                    textBlue.visibility = View.VISIBLE
                }, 150)
                colorPanel.postDelayed({
                    colorGreenBar.visibility = View.VISIBLE
                }, 200)
                colorPanel.postDelayed({
                    textGreen.visibility = View.VISIBLE
                }, 225)
                colorPanel.postDelayed({
                    colorRedBar.visibility = View.VISIBLE
                }, 275)
                colorPanel.postDelayed({
                    textRed.visibility = View.VISIBLE
                }, 300)
            }
        }

        colorRedBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seek: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val newColor = Color.rgb(
                    progress,
                    colorGreenBar.progress,
                    colorBlueBar.progress
                )
                with(prefs.edit()) {
                    putInt(SamSprung.prefColors, newColor)
                    apply()
                }
                colorComposite.setBackgroundColor(newColor)
                alphaPreview.setBackgroundColor(newColor)
                colorRedBar.progressTintList = ColorStateList
                    .valueOf(Color.rgb(progress, 0, 0))
            }

            override fun onStartTrackingTouch(seek: SeekBar) { }

            override fun onStopTrackingTouch(seek: SeekBar) { }
        })

        colorGreenBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seek: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val newColor = Color.rgb(
                    colorRedBar.progress,
                    progress,
                    colorBlueBar.progress
                )
                with(prefs.edit()) {
                    putInt(SamSprung.prefColors, newColor)
                    apply()
                }
                colorComposite.setBackgroundColor(newColor)
                alphaPreview.setBackgroundColor(newColor)
                colorGreenBar.progressTintList = ColorStateList
                    .valueOf(Color.rgb(0, progress, 0))
            }

            override fun onStartTrackingTouch(seek: SeekBar) { }

            override fun onStopTrackingTouch(seek: SeekBar) { }
        })

        colorBlueBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seek: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val newColor = Color.rgb(
                    colorRedBar.progress,
                    colorGreenBar.progress,
                    progress
                )
                with(prefs.edit()) {
                    putInt(SamSprung.prefColors, newColor)
                    apply()
                }
                colorComposite.setBackgroundColor(newColor)
                alphaPreview.setBackgroundColor(newColor)
                colorBlueBar.progressTintList = ColorStateList
                    .valueOf(Color.rgb(0, 0, progress))
            }

            override fun onStartTrackingTouch(seek: SeekBar) { }

            override fun onStopTrackingTouch(seek: SeekBar) { }
        })

        colorAlphaBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seek: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val alpha = progress.toFloat() / 100
                with(prefs.edit()) {
                    putFloat(SamSprung.prefAlphas, alpha)
                    apply()
                }
                alphaPreview.alpha = alpha
            }

            override fun onStartTrackingTouch(seek: SeekBar) { }

            override fun onStopTrackingTouch(seek: SeekBar) { }
        })

        textRed.visibility = View.GONE
        colorRedBar.visibility = View.GONE
        textGreen.visibility = View.GONE
        colorGreenBar.visibility = View.GONE
        textBlue.visibility = View.GONE
        colorBlueBar.visibility = View.GONE
        alphaView.visibility = View.GONE
        colorAlphaBar.visibility = View.GONE
        colorPanel.visibility = View.GONE

        val placementBar = findViewById<SeekBar>(R.id.placement_bar)
        placementBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seek: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                with(prefs.edit()) {
                    putInt(SamSprung.prefShifts, progress)
                    apply()
                }
            }

            override fun onStartTrackingTouch(seek: SeekBar) { }

            override fun onStopTrackingTouch(seek: SeekBar) { }
        })
        placementBar.progress = prefs.getInt(SamSprung.prefShifts, 2)

        val themeSpinner = findViewById<Spinner>(R.id.theme_spinner)
        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item,
            resources.getStringArray(R.array.theme_options))
        spinnerAdapter.setDropDownViewResource(R.layout.dropdown_item_1)
        themeSpinner.adapter = spinnerAdapter
        themeSpinner.setSelection(prefs.getInt(SamSprung.prefThemes, 0))
        themeSpinner.onItemSelectedListener = object: AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                with (prefs.edit()) {
                    putInt(SamSprung.prefThemes, position)
                    apply()
                }
                (application as SamSprung).setThemePreference()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) { }
        }

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        toolbar.inflateMenu(R.menu.cover_quick_toggles)

        toolbar.setOnMenuItemClickListener { item: MenuItem ->
            val pref = item.title?.toPref
            with(prefs.edit()) {
                putBoolean(pref, !prefs.getBoolean(pref, true))
                apply()
            }
            when (item.itemId) {
                R.id.toggle_wifi -> {
                    if (prefs.getBoolean(pref, true))
                        item.setIcon(R.drawable.ic_baseline_wifi_on_24dp)
                    else
                        item.setIcon(R.drawable.ic_baseline_wifi_off_24dp)
                    return@setOnMenuItemClickListener true
                }
                R.id.toggle_bluetooth -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        requestBluetooth.launch(Manifest.permission.BLUETOOTH_CONNECT)
                    } else {
                        toggleBluetoothIcon(toolbar)
                    }
                    return@setOnMenuItemClickListener true
                }
                R.id.toggle_nfc -> {
                    if (prefs.getBoolean(pref, true))
                        item.setIcon(R.drawable.ic_baseline_nfc_on_24dp)
                    else
                        item.setIcon(R.drawable.ic_baseline_nfc_off_24dp)
                    return@setOnMenuItemClickListener true
                }
                R.id.toggle_sound -> {
                    if (prefs.getBoolean(pref, true))
                        item.setIcon(R.drawable.ic_baseline_sound_on_24dp)
                    else
                        item.setIcon(R.drawable.ic_baseline_sound_off_24dp)
                    return@setOnMenuItemClickListener true
                }
                R.id.toggle_dnd -> {
                    if (prefs.getBoolean(pref, true))
                        item.setIcon(R.drawable.ic_baseline_do_not_disturb_on_24dp)
                    else
                        item.setIcon(R.drawable.ic_baseline_do_not_disturb_off_24dp)
                    return@setOnMenuItemClickListener true
                }
                R.id.toggle_torch -> {
                    if (prefs.getBoolean(pref, true))
                        item.setIcon(R.drawable.ic_baseline_flashlight_on_24dp)
                    else
                        item.setIcon(R.drawable.ic_baseline_flashlight_off_24dp)
                    return@setOnMenuItemClickListener true
                }
                R.id.toggle_widgets -> {
                    requestWidgets.launch(Manifest.permission.BIND_APPWIDGET)
                    return@setOnMenuItemClickListener true
                }
                else -> {
                    return@setOnMenuItemClickListener false
                }
            }
        }

        val wifi = toolbar.menu.findItem(R.id.toggle_wifi)
        if (prefs.getBoolean(wifi.title?.toPref, true))
            wifi.setIcon(R.drawable.ic_baseline_wifi_on_24dp)
        else
            wifi.setIcon(R.drawable.ic_baseline_wifi_off_24dp)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!hasPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                with(prefs.edit()) {
                    putBoolean(toolbar.menu.findItem(R.id.toggle_bluetooth).title?.toPref, false)
                    apply()
                }
            }
        }
        toggleBluetoothIcon(toolbar)

        val nfc = toolbar.menu.findItem(R.id.toggle_nfc)
        if (prefs.getBoolean(nfc.title?.toPref, true))
            nfc.setIcon(R.drawable.ic_baseline_nfc_on_24dp)
        else
            nfc.setIcon(R.drawable.ic_baseline_nfc_off_24dp)

        val sound = toolbar.menu.findItem(R.id.toggle_sound)
        if (prefs.getBoolean(sound.title?.toPref, true))
            sound.setIcon(R.drawable.ic_baseline_sound_on_24dp)
        else
            sound.setIcon(R.drawable.ic_baseline_sound_off_24dp)

        val dnd = toolbar.menu.findItem(R.id.toggle_dnd)
        if (prefs.getBoolean(dnd.title?.toPref, true))
            dnd.setIcon(R.drawable.ic_baseline_do_not_disturb_on_24dp)
        else
            dnd.setIcon(R.drawable.ic_baseline_do_not_disturb_off_24dp)

        val torch = toolbar.menu.findItem(R.id.toggle_torch)
        if (prefs.getBoolean(torch.title?.toPref, true))
            torch.setIcon(R.drawable.ic_baseline_flashlight_on_24dp)
        else
            torch.setIcon(R.drawable.ic_baseline_flashlight_off_24dp)

        toggleWidgetsIcon(toolbar)

        findViewById<LinearLayout>(R.id.menu_drawer).setOnClickListener {
            if (drawer.isGone && (general.isVisible || notices.isVisible)) {
                general.isGone = true
                notices.isGone = true
                drawer.postDelayed({
                    drawer.isVisible = true
                    nestedOptions.scrollToDescendant(drawer)
                }, 100)
            } else {
                drawer.isGone = drawer.isVisible
                nestedOptions.scrollToDescendant(drawer)
            }
        }
        drawer.isGone = true

        val vibration = findViewById<SwitchCompat>(R.id.vibration_switch)
        vibration.isChecked = prefs.getBoolean(SamSprung.prefReacts, true)
        vibration.setOnCheckedChangeListener { _, isChecked ->
            with(prefs.edit()) {
                putBoolean(SamSprung.prefReacts, isChecked)
                apply()
            }
        }
        findViewById<LinearLayout>(R.id.vibration).setOnClickListener {
            vibration.isChecked = !vibration.isChecked
        }

        val gestures = findViewById<SwitchCompat>(R.id.gestures_switch)
        gestures.isChecked = prefs.getBoolean(SamSprung.prefSlider, true)
        gestures.setOnCheckedChangeListener { _, isChecked ->
            with(prefs.edit()) {
                putBoolean(SamSprung.prefSlider, isChecked)
                apply()
            }
        }
        findViewById<LinearLayout>(R.id.gestures).setOnClickListener {
            gestures.isChecked = !gestures.isChecked
        }

        val animate = findViewById<SwitchCompat>(R.id.animate_switch)
        animate.isChecked = prefs.getBoolean(SamSprung.prefCarded, true)
        animate.setOnCheckedChangeListener { _, isChecked ->
            with(prefs.edit()) {
                putBoolean(SamSprung.prefCarded, isChecked)
                apply()
            }
        }
        findViewById<LinearLayout>(R.id.animate).setOnClickListener {
            animate.isChecked = !animate.isChecked
        }

        val lengthBar = findViewById<SeekBar>(R.id.length_bar)
        val lengthText = findViewById<TextView>(R.id.length_text)
        lengthBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seek: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                with(prefs.edit()) {
                    putInt(SamSprung.prefLength, progress)
                    apply()
                }
                val textLength = (if (lengthBar.progress < 6) lengthBar.progress else
                    DecimalFormatSymbols.getInstance().infinity).toString()
                setSuperscriptText(lengthText, R.string.options_length, textLength)
            }

            override fun onStartTrackingTouch(seek: SeekBar) { }

            override fun onStopTrackingTouch(seek: SeekBar) { }
        })
        lengthBar.progress = prefs.getInt(SamSprung.prefLength, 6)
        val textLength = (if (lengthBar.progress < 6) lengthBar.progress else
            DecimalFormatSymbols.getInstance().infinity).toString()
        setSuperscriptText(lengthText, R.string.options_length, textLength)

        val search = findViewById<SwitchCompat>(R.id.search_switch)
        search.setOnCheckedChangeListener { _, isChecked ->
            with(prefs.edit()) {
                putBoolean(SamSprung.prefSearch, isChecked && keyboard.isChecked)
                apply()
            }
        }
        search.isChecked = prefs.getBoolean(
            SamSprung.prefSearch, keyboard.isChecked
        ) && keyboard.isChecked
        findViewById<LinearLayout>(R.id.search).setOnClickListener {
            search.isChecked = !search.isChecked && keyboard.isChecked
            if (!keyboard.isChecked) {
                Toast.makeText(
                    this@CoverPreferences,
                    R.string.keyboard_missing, Toast.LENGTH_SHORT
                ).show()
            }
        }

        findViewById<LinearLayout>(R.id.wallpaper_layout).setOnClickListener {
            onPickImage.launch(Intent.createChooser(Intent(Intent.ACTION_OPEN_DOCUMENT)
                .setType("image/*").addCategory(Intent.CATEGORY_OPENABLE)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                .putExtra("android.content.extra.SHOW_ADVANCED", true)
                .putExtra("android.content.extra.FANCY", true), title))
        }
        findViewById<LinearLayout>(R.id.wallpaper_layout).setOnLongClickListener {
            val background = File(filesDir, "wallpaper.png")
            if (background.exists()) background.delete()
            val animated = File(filesDir, "wallpaper.gif")
            if (animated.exists()) animated.delete()
            Toast.makeText(this@CoverPreferences,
                R.string.wallpaper_cleared, Toast.LENGTH_SHORT).show()
            return@setOnLongClickListener true
        }

        val radius = findViewById<SwitchCompat>(R.id.radius_switch)
        radius.isChecked = prefs.getBoolean(SamSprung.prefRadius, true)
        radius.setOnCheckedChangeListener { _, isChecked ->
            with(prefs.edit()) {
                putBoolean(SamSprung.prefRadius, isChecked)
                apply()
            }
        }
        findViewById<LinearLayout>(R.id.radius).setOnClickListener {
            radius.isChecked = !radius.isChecked
        }

        val timeoutBar = findViewById<SeekBar>(R.id.timeout_bar)
        val timeoutText = findViewById<TextView>(R.id.timeout_text)
        timeoutBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seek: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                with(prefs.edit()) {
                    putInt(SamSprung.prefDelays, progress)
                    apply()
                }
                val textDelay = (if (timeoutBar.progress > 4) timeoutBar.progress else
                    DecimalFormatSymbols.getInstance().infinity).toString()
                setSuperscriptText(timeoutText, R.string.options_timeout, textDelay)
            }

            override fun onStartTrackingTouch(seek: SeekBar) { }

            override fun onStopTrackingTouch(seek: SeekBar) { }
        })
        timeoutBar.progress = prefs.getInt(SamSprung.prefDelays, 5)
        val textDelay = (if (timeoutBar.progress > 4) timeoutBar.progress else
            DecimalFormatSymbols.getInstance().infinity).toString()
        setSuperscriptText(timeoutText, R.string.options_timeout, textDelay)

        val isGridView = prefs.getBoolean(SamSprung.prefLayout, true)
        findViewById<ToggleButton>(R.id.swapViewType).isChecked = isGridView
        findViewById<ToggleButton>(R.id.swapViewType).setOnCheckedChangeListener { _, isChecked ->
            with (prefs.edit()) {
                putBoolean(SamSprung.prefLayout, isChecked)
                apply()
            }
        }

        findViewById<LinearLayout>(R.id.menu_notices).setOnClickListener {
            if (notices.isGone && (general.isVisible || drawer.isVisible)) {
                general.isGone = true
                drawer.isGone = true
                notices.postDelayed({
                    notices.isVisible = true
                    nestedOptions.scrollToDescendant(notices)
                }, 100)
            } else {
                notices.isGone = notices.isVisible
                nestedOptions.scrollToDescendant(notices)
            }
        }
        notices.isGone = true

        val dismissBar = findViewById<SeekBar>(R.id.dismiss_bar)
        val dismissText = findViewById<TextView>(R.id.dismiss_text)
        dismissBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seek: SeekBar, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                with(prefs.edit()) {
                    putInt(SamSprung.prefSnooze, progress * 10)
                    apply()
                }
                dismissText.text = getString(
                    R.string.options_dismiss, (dismissBar.progress * 10).toString()
                )
            }

            override fun onStartTrackingTouch(seek: SeekBar) { }

            override fun onStopTrackingTouch(seek: SeekBar) { }
        })
        dismissBar.progress = prefs.getInt(SamSprung.prefSnooze, 30) / 10
        dismissText.text = getString(
            R.string.options_dismiss, (dismissBar.progress * 10).toString()
        )

        val packageRetriever = PackageRetriever(this)
        val packages = packageRetriever.getPackageList()
        for (installed in packages) {
            if (installed.resolvePackageName == "apps.ijp.coveros") {
                val compatDialog = AlertDialog.Builder(this)
                    .setMessage(getString(R.string.incompatibility_warning))
                    .setPositiveButton(R.string.button_uninstall) { dialog, _ ->
                        try {
                            startActivity(Intent(Intent.ACTION_DELETE)
                                .setData(Uri.parse("package:apps.ijp.coveros")))
                            dialog.dismiss()
                        } catch (ignored: Exception) { }
                    }
                    .setNegativeButton(R.string.button_disable) { dialog, _ ->
                        startActivity(Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:apps.ijp.coveros")
                        ))
                        dialog.dismiss()
                    }.create()
                compatDialog.setCancelable(false)
                compatDialog.show()
            }
        }
        val unlisted = packageRetriever.getHiddenPackages()

        hiddenList = findViewById(R.id.app_toggle_list)
        hiddenList.layoutManager = LinearLayoutManager(this)
        hiddenList.addItemDecoration(
            DividerItemDecoration(this, DividerItemDecoration.VERTICAL)
        )
        hiddenList.adapter = FilteredAppsAdapter(packageManager, packages, unlisted, prefs)
        @Suppress("DEPRECATION")
        hiddenList.setOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    hiddenList.setIndexBarVisibility(true)
                } else if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    hiddenList.setIndexBarVisibility(false)
                }
            }
        })

        findViewById<View>(R.id.list_divider).setOnTouchListener { v: View, event: MotionEvent ->
            val y = event.y.toInt()
            val srcHeight = nestedOptions.layoutParams.height
            if (nestedOptions.layoutParams.height + y >= -0.5f) {
                if (event.action == MotionEvent.ACTION_MOVE) {
                    nestedOptions.layoutParams.height += y
                    if (srcHeight != nestedOptions.layoutParams.height) nestedOptions.requestLayout()
                } else if (event.action == MotionEvent.ACTION_UP) {
                    if (nestedOptions.layoutParams.height + y < 0f) {
                        nestedOptions.layoutParams.height = 0
                    } else {
                        val minHeight: Float = v.height + resources.getDimension(R.dimen.sliding_bar_margin)
                        if (nestedOptions.layoutParams.height > coordinator.height - minHeight.toInt())
                            nestedOptions.layoutParams.height = coordinator.height - minHeight.toInt()
                    }
                    if (srcHeight != nestedOptions.layoutParams.height) nestedOptions.requestLayout()
                }
            }
            true
        }

        val mWebView = findViewById<WebView>(R.id.webview_wiki)
        val webViewSettings: WebSettings = mWebView.settings
        mWebView.isScrollbarFadingEnabled = true
        webViewSettings.loadWithOverviewMode = true
        webViewSettings.useWideViewPort = true
        @SuppressLint("SetJavaScriptEnabled")
        webViewSettings.javaScriptEnabled = true
        webViewSettings.domStorageEnabled = true
        webViewSettings.cacheMode = WebSettings.LOAD_NO_CACHE
        webViewSettings.userAgentString = webViewSettings.userAgentString.replace(
            "(?i)" + Pattern.quote("android").toRegex(), "SamSprung"
        )
        mWebView.loadUrl("https://samsprung.github.io/launcher/")
    }

    private fun setAnimatedUpdateNotice(appUpdateInfo: AppUpdateInfo?, downloadUrl: String?) {
        runOnUiThread {
            val buildIcon = findViewById<AppCompatImageView>(R.id.build_icon)
            buildIcon.setImageDrawable(ContextCompat.getDrawable(
                this@CoverPreferences, R.drawable.ic_baseline_browser_updated_24dp))
            val buildInfo = findViewById<TextView>(R.id.build_info)
            val colorStateList = buildInfo.textColors
            buildInfo.setTextColor(Color.RED)
            val anim: Animation = AlphaAnimation(0.2f, 1.0f)
            anim.duration = 500
            anim.repeatMode = Animation.REVERSE
            anim.repeatCount = Animation.INFINITE
            buildInfo.startAnimation(anim)
            findViewById<LinearLayout>(R.id.build_layout).setOnClickListener {
                buildIcon.setImageDrawable(ContextCompat.getDrawable(
                    this@CoverPreferences, R.drawable.ic_github_octocat_24dp))
                anim.cancel()
                buildInfo.setTextColor(colorStateList)
                if (null != appUpdateInfo) {
                    updateCheck?.downloadPlayUpdate(appUpdateInfo)
                } else if (null != downloadUrl) {
                    updateCheck?.downloadUpdate(downloadUrl)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private val requestStorage = registerForActivityResult(
        ActivityResultContracts.RequestPermission()) {
        if (it) coordinator.background = WallpaperManager.getInstance(this).drawable

        updateCheck = CheckUpdatesTask(this@CoverPreferences)
        if (SamSprung.isGooglePlay()) {
            updateCheck?.setPlayUpdateListener(object: CheckUpdatesTask.CheckPlayUpdateListener {
                override fun onPlayUpdateFound(appUpdateInfo: AppUpdateInfo) {
                    setAnimatedUpdateNotice(appUpdateInfo, null)
                }
            })
        } else {
            updateCheck?.setUpdateListener(object: CheckUpdatesTask.CheckUpdateListener {
                override fun onUpdateFound(downloadUrl: String) {
                    setAnimatedUpdateNotice(null, downloadUrl)
                }
            })
        }
    }

    private fun saveAnimatedImage(sourceUri: Uri) {
        val background = File(filesDir, "wallpaper.png")
        if (background.exists()) background.delete()
        Executors.newSingleThreadExecutor().execute {
            val destinationFilename = File(filesDir, "wallpaper.gif")
            var bis: BufferedInputStream? = null
            var bos: BufferedOutputStream? = null
            try {
                bis = BufferedInputStream(contentResolver.openInputStream(sourceUri))
                bos = BufferedOutputStream(FileOutputStream(destinationFilename, false))
                val buf = ByteArray(1024)
                bis.read(buf)
                do {
                    bos.write(buf)
                } while (bis.read(buf) != -1)
            } catch (e: IOException) {
                e.printStackTrace()
            } finally {
                try {
                    bis?.close()
                    bos?.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun saveStaticImage(sourceUri: Uri) {
        val animated = File(filesDir, "wallpaper.gif")
        if (animated.exists()) animated.delete()
        Executors.newSingleThreadExecutor().execute {
            val source: ImageDecoder.Source = ImageDecoder.createSource(
                this.contentResolver, sourceUri
            )
            val bitmap: Bitmap = ImageDecoder.decodeBitmap(source)
            var rotation = -1
            val background = File(filesDir, "wallpaper.png")
            val cursor: Cursor? = contentResolver.query(
                sourceUri, arrayOf(MediaStore.Images.ImageColumns.ORIENTATION),
                null, null, null
            )
            if (cursor?.count == 1) {
                cursor.moveToFirst()
                rotation = cursor.getInt(0)
            }
            if (rotation > 0) {
                val matrix = Matrix()
                matrix.postRotate(rotation.toFloat())
                background.writeBitmap(Bitmap.createBitmap(bitmap, 0, 0, bitmap.width,
                    bitmap.height, matrix, true), Bitmap.CompressFormat.PNG, 100)
            } else {
                background.writeBitmap(bitmap, Bitmap.CompressFormat.PNG, 100)
            }
            cursor?.close()
        }
    }

    private val onPickImage = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK && null != result.data) {
            var photoUri: Uri? = null
            if (null != result.data!!.clipData) {
                photoUri = result.data!!.clipData!!.getItemAt(0)!!.uri
            } else if (null != result.data!!.data) {
                photoUri = result.data!!.data!!
            }
            if (null != photoUri) {
                val extension: String? = when {
                    photoUri.scheme == ContentResolver.SCHEME_CONTENT -> {
                        MimeTypeMap.getSingleton().getExtensionFromMimeType(
                            contentResolver.getType(photoUri)
                        )
                    } null != photoUri.path -> {
                        MimeTypeMap.getFileExtensionFromUrl(
                            Uri.fromFile(File(photoUri.path!!)).toString()
                        )
                    } else -> {
                        null
                    }
                }
                if (extension.equals("gif", true))
                    saveAnimatedImage(photoUri)
                else
                    saveStaticImage(photoUri)
            }
        }
    }

    private fun toggleVoiceIcon(isEnabled: Boolean) {
        findViewById<AppCompatImageView>(R.id.voice_button).setImageResource(
            if (isEnabled)
                R.drawable.ic_baseline_record_voice_over_24dp
            else
                R.drawable.ic_baseline_voice_over_off_24dp
        )
    }

    private val requestVoice = registerForActivityResult(
        ActivityResultContracts.RequestPermission()) { toggleVoiceIcon(it) }

    private fun toggleBluetoothIcon(toolbar: Toolbar) {
        val bluetooth = toolbar.menu.findItem(R.id.toggle_bluetooth)
        if (prefs.getBoolean(bluetooth.title?.toPref, true))
            bluetooth.setIcon(R.drawable.ic_baseline_bluetooth_on_24dp)
        else
            bluetooth.setIcon(R.drawable.ic_baseline_bluetooth_off_24dp)
    }

    @SuppressLint("MissingPermission")
    private val requestBluetooth = registerForActivityResult(
        ActivityResultContracts.RequestPermission()) {
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        if (!it) {
            with(prefs.edit()) {
                putBoolean(toolbar.menu.findItem(R.id.toggle_bluetooth).title?.toPref, false)
                apply()
            }
        }
        toggleBluetoothIcon(toolbar)
    }

    private fun toggleWidgetsIcon(toolbar: Toolbar) {
        val widgets = toolbar.menu.findItem(R.id.toggle_widgets)
        if (prefs.getBoolean(widgets.title?.toPref, false))
            widgets.setIcon(R.drawable.ic_baseline_widgets_24dp)
        else
            widgets.setIcon(R.drawable.ic_baseline_insert_page_break_24dp)
    }

    private val overlayLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()) {
        if (this::mainSwitch.isInitialized) {
            mainSwitch.isChecked = Settings.canDrawOverlays(applicationContext)
            startForegroundService(Intent(
                ScaledContext.cover(this), OnBroadcastService::class.java
            ))
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private val usageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()) {
        Executors.newSingleThreadExecutor().execute {
            val packageRetriever = PackageRetriever(this)
            val packages = packageRetriever.getPackageList()
            val unlisted = packageRetriever.getHiddenPackages()
            runOnUiThread {
                val adapter = hiddenList.adapter as FilteredAppsAdapter
                adapter.setPackages(packages, unlisted)
                adapter.notifyDataSetChanged()
            }
        }
        if (this::statistics.isInitialized)
            statistics.isChecked = hasUsageStatistics()
    }

    private val optimizationLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()) {
        if (this::optimization.isInitialized)
            optimization.isChecked = ignoreBatteryOptimization()
    }

    private val accessibilityLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()) {
        if (this::accessibility.isInitialized)
            accessibility.isChecked = hasAccessibility()
    }

    private val keyboardLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()) {
        if (this::keyboard.isInitialized)
            keyboard.isChecked = hasKeyboardInstalled()
    }

    private val notificationLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()) {
        if (this::notifications.isInitialized)
            notifications.isChecked = hasNotificationListener()
    }

    private val requestWidgets = registerForActivityResult(
        ActivityResultContracts.RequestPermission()) {
        toggleWidgetsIcon(findViewById(R.id.toolbar))
    }

    private fun setSuperscriptText(view: TextView, resource: Int, value: String) {
        val text = SpannableStringBuilder(getString(resource, value))
        text.setSpan(
            RelativeSizeSpan(0.75f),
            text.length - value.length - 1, text.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        view.text = text
    }

    private fun isDeviceSecure(): Boolean {
        return (getSystemService(KEYGUARD_SERVICE) as KeyguardManager).isDeviceSecure
    }

    private fun hasPermission(permission: String) : Boolean {
        return (checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED)
    }

    private fun ignoreBatteryOptimization(): Boolean {
        return ((getSystemService(POWER_SERVICE) as PowerManager)
            .isIgnoringBatteryOptimizations(packageName))
    }

    private fun hasAccessibility(): Boolean {
        val serviceString = Settings.Secure.getString(contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        return serviceString != null && serviceString.contains(packageName
                + File.separator + AccessibilityObserver::class.java.name)
    }

    private fun hasNotificationListener(): Boolean {
        val myNotificationListenerComponentName = ComponentName(
            applicationContext, NotificationReceiver::class.java)
        val enabledListeners = Settings.Secure.getString(
            contentResolver, "enabled_notification_listeners")
        if (enabledListeners.isEmpty()) return false
        return enabledListeners.split(":").map {
            ComponentName.unflattenFromString(it)
        }.any {componentName->
            myNotificationListenerComponentName == componentName
        }
    }

    private fun hasUsageStatistics() : Boolean {
        try {
            if ((getSystemService(APP_OPS_SERVICE) as AppOpsManager).unsafeCheckOp(
                    "android:get_usage_stats", Process.myUid(), packageName
                ) == AppOpsManager.MODE_ALLOWED) return true
        } catch (ignored: SecurityException) { }
        return false
    }

    @Suppress("DEPRECATION")
    private fun hasKeyboardInstalled(): Boolean {
        return try {
            packageManager.getPackageInfo(BuildConfig.APPLICATION_ID + ".ime", 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    val updateLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()) {
        if (packageManager.canRequestPackageInstalls())
            updateCheck?.retrieveUpdate()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.cover_settings_menu, menu)
        val actionSwitch: MenuItem = menu.findItem(R.id.switch_action_bar)
        actionSwitch.setActionView(R.layout.configure_switch)
        mainSwitch = menu.findItem(R.id.switch_action_bar).actionView
            ?.findViewById(R.id.switch2) as SwitchCompat
        mainSwitch.isChecked = Settings.canDrawOverlays(applicationContext)
        mainSwitch.setOnClickListener {
            overlayLauncher.launch(Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            ))
        }
        return true
    }

    private fun onSendDonationClicked() {
        val view: LinearLayout = layoutInflater
            .inflate(R.layout.donation_layout, null) as LinearLayout
        val dialog = AlertDialog.Builder(
            ContextThemeWrapper(this, R.style.DialogTheme_NoActionBar)
        )
        val donations = view.findViewById<LinearLayout>(R.id.donation_layout)
        for (skuDetail: ProductDetails in iapSkuDetails
            .sortedBy { skuDetail -> skuDetail.productId }) {
            if (null == skuDetail.oneTimePurchaseOfferDetails) continue
            donations.addView(getDonationButton(skuDetail))
        }
        val subscriptions = view.findViewById<LinearLayout>(R.id.subscription_layout)
        for (skuDetail: ProductDetails in subSkuDetails
            .sortedBy { skuDetail -> skuDetail.productId }) {
            if (null == skuDetail.subscriptionOfferDetails) continue
            subscriptions.addView(getSubscriptionButton(skuDetail))
        }
        dialog.setOnCancelListener {
            donations.removeAllViewsInLayout()
            subscriptions.removeAllViewsInLayout()
        }
        val donateDialog: Dialog = dialog.setView(view).show()
        if (!SamSprung.isGooglePlay()) {
            @SuppressLint("InflateParams")
            val paypal: View = layoutInflater.inflate(R.layout.button_paypal, null)
            paypal.setOnClickListener {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(
                    "https://www.paypal.com/donate/?hosted_button_id=Q2LFH2SC8RHRN"
                )))
                donateDialog.cancel()
            }
            view.addView(paypal)
        }
        donateDialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    private fun initializeLayout() {
        startForegroundService(Intent(
            ScaledContext.cover(this), OnBroadcastService::class.java
        ))
        if (!prefs.getBoolean(SamSprung.prefWarned, false)) {
            wikiDrawer.openDrawer(GravityCompat.START)
            with(prefs.edit()) {
                putBoolean(SamSprung.prefWarned, true)
                apply()
            }
        }
        requestStorage.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        initializeLayout()
    }

    private var widgetNotice : Snackbar? = null
    private fun setLoadCompleted() {
        val onBackPressedCallback = object: OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    wikiDrawer.isDrawerOpen(GravityCompat.START) ->
                        wikiDrawer.closeDrawer(GravityCompat.START)
                    null == widgetNotice -> {
                        val social = findViewById<LinearLayout>(R.id.social_menu)
                        widgetNotice = IconifiedSnackbar(
                            this@CoverPreferences, social
                        ).buildTickerBar(
                            if (mainSwitch.isChecked)
                                getString(R.string.cover_widget_warning)
                            else
                                getString(R.string.cover_widget_warning)
                                        + getString(R.string.cover_switch_warning),
                            Snackbar.LENGTH_INDEFINITE)
                        social.postDelayed({
                            widgetNotice?.show()
                        }, 250)
                    }
                    else -> {
                        widgetNotice?.dismiss()
                        finish()
                    }
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)
    }

    override fun onRestart() {
        setLoadCompleted()
        super.onRestart()
    }

    private fun File.writeBitmap(bitmap: Bitmap, format: Bitmap.CompressFormat, quality: Int) {
        outputStream().use { out ->
            bitmap.compress(format, quality, out)
            out.flush()
        }
    }

    private fun getIAP(amount: Int) : String {
        return String.format("subscription_%02d", amount)
    }

    private fun getSub(amount: Int) : String {
        return String.format("monthly_%02d", amount)
    }

    private val iapList = ArrayList<String>()
    private val subList = ArrayList<String>()

    private val consumeResponseListener = ConsumeResponseListener { _, _ ->
        IconifiedSnackbar(this).buildTickerBar(getString(R.string.donation_thanks)).show()
    }

    private fun handlePurchaseIAP(purchase : Purchase) {
        val consumeParams = ConsumeParams.newBuilder().setPurchaseToken(purchase.purchaseToken)
        billingClient.consumeAsync(consumeParams.build(), consumeResponseListener)
    }

    private var acknowledgePurchaseResponseListener = AcknowledgePurchaseResponseListener {
        IconifiedSnackbar(this).buildTickerBar(getString(R.string.donation_thanks)).show()
        hasPremiumSupport = true
    }

    private fun handlePurchaseSub(purchase : Purchase) {
        val acknowledgePurchaseParams = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
        billingClient.acknowledgePurchase(acknowledgePurchaseParams.build(),
            acknowledgePurchaseResponseListener)
    }

    private fun handlePurchase(purchase : Purchase) {
        if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
            if (!purchase.isAcknowledged) {
                for (iap: String in iapList) {
                    if (purchase.products.contains(iap))
                        handlePurchaseIAP(purchase)
                }
                for (sub: String in subList) {
                    if (purchase.products.contains(sub))
                        handlePurchaseSub(purchase)
                }
            }
        }
    }

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && null != purchases) {
            for (purchase in purchases) {
                handlePurchase(purchase)
            }
        }
    }

    private lateinit var subsPurchased: ArrayList<String>

    private val subsOwnedListener = PurchasesResponseListener { billingResult, purchases ->
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
            for (purchase in purchases) {
                for (sku in purchase.products) {
                    if (subsPurchased.contains(sku)) {
                        hasPremiumSupport = true
                        break
                    }
                }
            }
        }
    }

    private val subHistoryListener = PurchaseHistoryResponseListener { billingResult, purchases ->
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && null != purchases) {
            for (purchase in purchases)
                subsPurchased.addAll(purchase.products)
            billingClient.queryPurchasesAsync(QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.SUBS).build(), subsOwnedListener)
        }
    }

    private val iapHistoryListener = PurchaseHistoryResponseListener { billingResult, purchases ->
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK && null != purchases) {
            for (purchase in purchases) {
                for (sku in purchase.products) {
                    if (sku.split("_")[1].toInt() >= 10) {
                        hasPremiumSupport = true
                        break
                    }
                }
            }
        }
    }

    private val billingConnectionMutex = Mutex()

    private val resultAlreadyConnected = BillingResult.newBuilder()
        .setResponseCode(BillingClient.BillingResponseCode.OK)
        .setDebugMessage("Billing client is already connected")
        .build()

    /**
     * Returns immediately if this BillingClient is already connected, otherwise
     * initiates the connection and suspends until this client is connected.
     * If a connection is already in the process of being established, this
     * method just suspends until the billing client is ready.
     */
    private suspend fun BillingClient.connect(): BillingResult = billingConnectionMutex.withLock {
        if (isReady) {
            // fast path: avoid suspension if already connected
            resultAlreadyConnected
        } else {
            unsafeConnect()
        }
    }

    private suspend fun BillingClient.unsafeConnect() = suspendCoroutine<BillingResult> { cont ->
        startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                cont.resume(billingResult)
            }
            override fun onBillingServiceDisconnected() {
                // no need to setup reconnection logic here, call ensureReady()
                // before each purchase to reconnect as necessary
            }
        })
    }

    private fun getBillingConnection() {
        billingClient = BillingClient.newBuilder(this)
            .setListener(purchasesUpdatedListener).enablePendingPurchases().build()

        iapSkuDetails.clear()
        subSkuDetails.clear()

        CoroutineScope(Dispatchers.IO).launch(Dispatchers.IO) {
            val clientResponseCode = billingClient.connect().responseCode
            if (clientResponseCode == BillingClient.BillingResponseCode.OK) {
                iapList.add(getIAP(1))
                iapList.add(getIAP(5))
                iapList.add(getIAP(10))
                iapList.add(getIAP(25))
                iapList.add(getIAP(50))
                iapList.add(getIAP(75))
                iapList.add(getIAP(99))
                for (productId: String in iapList) {
                    val productList = QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(productId)
                        .setProductType(BillingClient.ProductType.INAPP).build()
                    val params = QueryProductDetailsParams.newBuilder()
                        .setProductList(listOf(productList))
                    billingClient.queryProductDetailsAsync(params.build()) { _, productDetailsList ->
                        iapSkuDetails.addAll(productDetailsList)
                        billingClient.queryPurchaseHistoryAsync(
                            QueryPurchaseHistoryParams.newBuilder().setProductType(
                                BillingClient.ProductType.INAPP
                            ).build(), iapHistoryListener
                        )
                    }
                }
                subList.add(getSub(1))
                subList.add(getSub(5))
                subList.add(getSub(10))
                subList.add(getSub(25))
                subList.add(getSub(50))
                subList.add(getSub(75))
                subList.add(getSub(99))
                for (productId: String in subList) {
                    val productList = QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(productId)
                        .setProductType(BillingClient.ProductType.SUBS).build()
                    val params = QueryProductDetailsParams.newBuilder()
                        .setProductList(listOf(productList))
                    billingClient.queryProductDetailsAsync(params.build()) { _, productDetailsList ->
                        subSkuDetails.addAll(productDetailsList)
                        billingClient.queryPurchaseHistoryAsync(
                            QueryPurchaseHistoryParams.newBuilder().setProductType(
                                BillingClient.ProductType.SUBS
                            ).build(), subHistoryListener
                        )
                    }
                }
            }
        }
    }

    private fun getDonationButton(skuDetail: ProductDetails): Button {
        val button = Button(applicationContext)
        button.setBackgroundResource(R.drawable.button_rippled)
        button.elevation = 10f.toPx
        button.setTextColor(ContextCompat.getColor(this, android.R.color.black))
        button.text = getString(
            R.string.iap_button, skuDetail
                .oneTimePurchaseOfferDetails!!.formattedPrice
        )
        button.setOnClickListener {
            val productDetailsParamsList = BillingFlowParams.ProductDetailsParams
                .newBuilder().setProductDetails(skuDetail).build()
            billingClient.launchBillingFlow(this, BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(productDetailsParamsList)).build()
            )
        }
        return button
    }

    private fun getSubscriptionButton(skuDetail: ProductDetails): Button {
        val button = Button(applicationContext)
        button.setBackgroundResource(R.drawable.button_rippled)
        button.elevation = 10f.toPx
        button.setTextColor(ContextCompat.getColor(this, android.R.color.black))
        button.text = getString(
            R.string.sub_button, skuDetail
                .subscriptionOfferDetails!![0].pricingPhases.pricingPhaseList[0].formattedPrice
        )
        button.setOnClickListener {
            val productDetailsParamsList = BillingFlowParams.ProductDetailsParams.newBuilder()
                .setOfferToken(skuDetail.subscriptionOfferDetails!![0]!!.offerToken)
                .setProductDetails(skuDetail).build()
            billingClient.launchBillingFlow(this, BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(productDetailsParamsList)).build()
            )
        }
        return button
    }
}