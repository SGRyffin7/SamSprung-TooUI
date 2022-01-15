package com.sec.android.app.shealth

/* ====================================================================
 * Copyright (c) 2012-2021 AbandonedCart.  All rights reserved.
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

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.notification.StatusBarNotification
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import java.util.*


class SamSprungHomeView : AppCompatActivity() {

    @SuppressLint("InflateParams", "CutPasteId", "ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.launcher_layout)

        findViewById<LinearLayout>(R.id.rootLayout).setOnTouchListener(
            object: OnSwipeTouchListener(this@SamSprungHomeView) {
            override fun onSwipeLeft() {

            }
            override fun onSwipeRight() {

            }
        })

        for (notice : StatusBarNotification in SamSprung.statuses) {
            val card : View = layoutInflater.inflate(R.layout.notification_card, null)
            card.findViewById<ImageView>(R.id.icon).setImageDrawable(
                notice.notification.smallIcon.loadDrawable(this))
            card.findViewById<TextView>(R.id.ticker).text = notice.notification.tickerText
            if (null != notice.notification.extras) {
                if (notice.notification.extras.getCharSequenceArray(
                        NotificationCompat.EXTRA_TEXT_LINES) != null) {
                    val content = Arrays.toString(
                        notice.notification.extras.getCharSequenceArray(
                            NotificationCompat.EXTRA_TEXT_LINES)
                    )
                    if (notice.notification.tickerText == content)
                        card.findViewById<TextView>(R.id.ticker).visibility = View.GONE
                    card.findViewById<TextView>(R.id.lines).text = content
                }

            }
            card.setOnClickListener {
                startIntentSender(notice.notification.contentIntent.intentSender,
                    null, 0, 0, 0)
            }

            findViewById<ListView>(R.id.notificationList).addView(card)
        }
    }
}