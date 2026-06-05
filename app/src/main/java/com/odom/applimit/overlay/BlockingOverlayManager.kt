package com.odom.applimit.overlay

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.odom.applimit.MainActivity
import com.odom.applimit.R

class BlockingOverlayManager(private val context: Context) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var overlayView: BlockingLayout? = null
    var currentPackage: String? = null
        private set

    fun canDrawOverlays(): Boolean = Settings.canDrawOverlays(context)

    fun show(packageName: String, appName: String, usedMinutes: Int, limitMinutes: Int) {
        if (!canDrawOverlays()) return
        if (overlayView != null && currentPackage == packageName) return
        hide()

        // No NOT_FOCUSABLE, no NOT_TOUCH_MODAL, no NOT_TOUCHABLE.
        // This makes the window a true modal: it receives all key + touch events
        // and nothing reaches the app below.
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        val innerContent = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(64, 64, 64, 64)
            addView(TextView(context).apply {
                text = appName
                textSize = 30f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
            })
            addView(TextView(context).apply {
                text = context.getString(R.string.overlay_limit_reached)
                textSize = 20f
                setTextColor(Color.argb(220, 255, 80, 80))
                gravity = Gravity.CENTER
                setPadding(0, 24, 0, 8)
            })
            addView(TextView(context).apply {
                text = context.getString(R.string.overlay_usage_stats, usedMinutes, limitMinutes)
                textSize = 16f
                setTextColor(Color.LTGRAY)
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, 48)
            })
            addView(Button(context).apply {
                text = context.getString(R.string.overlay_open_app)
                setOnClickListener { openAppLimit() }
            })
        }
        val bannerAd = AdView(context).apply {
            setAdSize(AdSize.BANNER)
            adUnitId = context.getString(R.string.admob_banner_id)
            loadAd(AdRequest.Builder().build())
        }
        val container = BlockingLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.argb(242, 10, 10, 20))
            isFocusable = true
            isFocusableInTouchMode = true
            addView(innerContent, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            ))
            addView(bannerAd, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }

        try {
            windowManager.addView(container, params)
            // Request focus so key events (Back, volume, etc.) are dispatched here
            container.requestFocus()
            overlayView = container
            currentPackage = packageName
        } catch (_: Exception) {}
    }

    fun hide() {
        overlayView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
        }
        overlayView = null
        currentPackage = null
    }

    fun isShowing(): Boolean = overlayView != null

    private fun openAppLimit() {
        context.startActivity(
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(MainActivity.EXTRA_FROM_BLOCKER, true)
            }
        )
    }

    /**
     * Full-screen blocking view. Overrides both dispatch methods so no touch or
     * key event ever reaches the window below.
     *
     * dispatchTouchEvent: calls super so the child Button can fire its click
     * listener, then always returns true (event consumed by this window).
     *
     * onTouchEvent: handles taps on the background (outside the button) by
     * navigating home.
     *
     * dispatchKeyEvent: returns true for everything — Back, volume, menu, etc.
     */
    private inner class BlockingLayout(context: Context) : LinearLayout(context) {

        override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
            super.dispatchTouchEvent(ev) // let Button's OnClickListener fire
            return true                  // then consume — nothing leaks to the app below
        }

        override fun onTouchEvent(ev: MotionEvent): Boolean {
            if (ev.action == MotionEvent.ACTION_UP) openAppLimit()
            return true
        }

        override fun dispatchKeyEvent(event: KeyEvent): Boolean = true
    }
}
