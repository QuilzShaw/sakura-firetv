package com.sakura.home

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Display
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView

/**
 * Mutes HBO Max and Hulu ad breaks. Neither app exposes playback info to other apps, but
 * during ads each draws a yellow "Ad" badge in a fixed corner, and that badge stays
 * visible in screenshots even though the protected video itself is black.
 * While one of them is in front, we glance at its badge corner once a second.
 */
class AdMuter(private val service: AccessibilityService) {

    companion object {
        /** Where an app draws its "Ad" badge, as fractions of the screen, and how yellow it is. */
        private class Badge(
            val x0: Float, val x1: Float, val y0: Float, val y1: Float,
            val minGreen: Int, val share: Float,
            val clearChecks: Int, // clear checks in a row before unmuting
        )

        private val badges = mapOf(
            // Top-left, measured at 1920x1080: ~104..166 x 74..114; fills ~50% of the box
            "com.hbo.hbonow" to Badge(0.045f, 0.095f, 0.060f, 0.115f, minGreen = 190, share = 0.18f, clearChecks = 2),
            // Top-right next to the countdown, 1778..1823 x 60..95; fills ~87%, color (242,188,60).
            // Some checks during Hulu ads miss the badge, so wait longer before unmuting
            "com.hulu.plus" to Badge(0.926f, 0.950f, 0.056f, 0.088f, minGreen = 160, share = 0.5f, clearChecks = 5),
        )

        private const val CHECK_MS = 1000L
    }

    private val handler = Handler(Looper.getMainLooper())
    private val audio = service.getSystemService(AudioManager::class.java)
    private val prefs = Prefs(service)
    private var running = false
    private var current: Badge? = null
    // Saved, so that if the service restarts mid-ad the new one still knows to unmute
    private val state = service.getSharedPreferences("sakura", Context.MODE_PRIVATE)
    private var weMuted: Boolean
        get() = state.getBoolean("ad_muted", false)
        set(value) { state.edit().putBoolean("ad_muted", value).commit() }
    private var clearCount = 0
    private var badge: TextView? = null

    init {
        // A previous run muted an ad and never got to unmute (e.g. Sakura was updated mid-ad)
        unmuteIfOurs()
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            if (!prefs.muteHboAds) {
                unmuteIfOurs()
            } else {
                service.takeScreenshot(Display.DEFAULT_DISPLAY, service.mainExecutor, object : TakeScreenshotCallback {
                    override fun onSuccess(result: ScreenshotResult) {
                        val hw = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                        result.hardwareBuffer.close()
                        val badge = current
                        val adShowing = if (hw != null && badge != null) isAdBadge(hw, badge) else false
                        hw?.recycle()
                        // Results can arrive after the app left the front or the service stopped
                        if (running) onResult(adShowing)
                    }

                    override fun onFailure(errorCode: Int) {}
                })
            }
            handler.postDelayed(this, CHECK_MS)
        }
    }

    fun setForeground(pkg: String) {
        val badge = badges[pkg]
        current = badge
        if (badge != null && !running) {
            running = true
            handler.post(tick)
        } else if (badge == null && running) {
            running = false
            handler.removeCallbacks(tick)
            unmuteIfOurs()
        }
    }

    private fun isAdBadge(hw: Bitmap, b: Badge): Boolean {
        val w = hw.width
        val h = hw.height
        val x0 = (w * b.x0).toInt()
        val y0 = (h * b.y0).toInt()
        val cw = (w * (b.x1 - b.x0)).toInt()
        val ch = (h * (b.y1 - b.y0)).toInt()
        // Hardware bitmaps can't be read directly; copy once, then read only the corner
        val soft = hw.copy(Bitmap.Config.ARGB_8888, false) ?: return false
        val px = IntArray(cw * ch)
        soft.getPixels(px, 0, cw, x0, y0, cw, ch)
        soft.recycle()
        var yellow = 0
        for (c in px) {
            if (Color.red(c) > 200 && Color.green(c) > b.minGreen && Color.blue(c) < 110) yellow++
        }
        return yellow > px.size * b.share
    }

    private fun onResult(adShowing: Boolean) {
        if (adShowing) {
            clearCount = 0
            if (!weMuted && !audio.isStreamMute(AudioManager.STREAM_MUSIC)) {
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
                weMuted = true
                showBadge(true)
            }
        } else if (weMuted && ++clearCount >= (current?.clearChecks ?: 1)) {
            unmuteIfOurs()
        }
    }

    private fun unmuteIfOurs() {
        if (weMuted) {
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
            weMuted = false
        }
        clearCount = 0
        showBadge(false)
    }

    // Small pink "Ad muted" tag in a top corner while an ad is silenced
    private fun showBadge(show: Boolean) {
        val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        if (show && badge == null) {
            val d = service.resources.displayMetrics.density
            val tv = TextView(service).apply {
                text = "✿  Ad muted"
                setTextColor(Color.parseColor("#2A1430"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setPadding((14 * d).toInt(), (6 * d).toInt(), (14 * d).toInt(), (6 * d).toInt())
                background = GradientDrawable().apply {
                    cornerRadius = 20 * d
                    colors = intArrayOf(Color.parseColor("#FFFF8FB6"), Color.parseColor("#FFFFC2D6"))
                    orientation = GradientDrawable.Orientation.LEFT_RIGHT
                }
                alpha = 0f
            }
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT,
            ).apply {
                // Opposite corner from the app's own badge: covering it would read as "ad over"
                val badgeOnRight = (current?.x0 ?: 0f) > 0.5f
                gravity = Gravity.TOP or (if (badgeOnRight) Gravity.START else Gravity.END)
                x = (32 * d).toInt()
                y = (28 * d).toInt()
            }
            // Fails if the service is being disconnected; the mute itself still works
            if (runCatching { wm.addView(tv, lp) }.isFailure) return
            tv.animate().alpha(1f).setDuration(250).start()
            badge = tv
        } else if (!show) {
            badge?.let { v -> runCatching { wm.removeView(v) } }
            badge = null
        }
    }

    fun shutdown() {
        running = false
        handler.removeCallbacks(tick)
        unmuteIfOurs()
    }
}
