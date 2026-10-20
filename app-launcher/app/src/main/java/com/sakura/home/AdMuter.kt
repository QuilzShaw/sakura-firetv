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
 * Mutes HBO Max ad breaks. HBO Max exposes no playback info to other apps, but during
 * ads it always draws a bright yellow "Ad" badge in the top-left corner, and that badge
 * stays visible in screenshots even though the protected video itself is black.
 * While HBO Max is in front, we glance at that corner once a second.
 */
class AdMuter(private val service: AccessibilityService) {

    companion object {
        const val HBO = "com.hbo.hbonow"

        // Badge area as fractions of the screen (measured at 1920x1080: ~104..166 x 74..114)
        private const val X0 = 0.045f
        private const val X1 = 0.095f
        private const val Y0 = 0.060f
        private const val Y1 = 0.115f
        private const val YELLOW_SHARE = 0.18f // badge fills ~50% of the box; text/video never this yellow
        private const val CHECK_MS = 1000L
        private const val CLEAR_CHECKS_TO_UNMUTE = 2
    }

    private val handler = Handler(Looper.getMainLooper())
    private val audio = service.getSystemService(AudioManager::class.java)
    private val prefs = Prefs(service)
    private var running = false
    private var weMuted = false
    private var clearCount = 0
    private var badge: TextView? = null

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
                        val adShowing = hw?.let { isAdBadge(it) } ?: false
                        hw?.recycle()
                        onResult(adShowing)
                    }

                    override fun onFailure(errorCode: Int) {}
                })
            }
            handler.postDelayed(this, CHECK_MS)
        }
    }

    fun setForeground(pkg: String) {
        val hbo = pkg == HBO
        if (hbo && !running) {
            running = true
            handler.post(tick)
        } else if (!hbo && running) {
            running = false
            handler.removeCallbacks(tick)
            unmuteIfOurs()
        }
    }

    private fun isAdBadge(hw: Bitmap): Boolean {
        val w = hw.width
        val h = hw.height
        val x0 = (w * X0).toInt()
        val y0 = (h * Y0).toInt()
        val cw = (w * (X1 - X0)).toInt()
        val ch = (h * (Y1 - Y0)).toInt()
        // Hardware bitmaps can't be read directly; copy once, then read only the corner
        val soft = hw.copy(Bitmap.Config.ARGB_8888, false) ?: return false
        val px = IntArray(cw * ch)
        soft.getPixels(px, 0, cw, x0, y0, cw, ch)
        soft.recycle()
        var yellow = 0
        for (c in px) {
            if (Color.red(c) > 200 && Color.green(c) > 190 && Color.blue(c) < 110) yellow++
        }
        return yellow > px.size * YELLOW_SHARE
    }

    private fun onResult(adShowing: Boolean) {
        if (adShowing) {
            clearCount = 0
            if (!weMuted && !audio.isStreamMute(AudioManager.STREAM_MUSIC)) {
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
                weMuted = true
                showBadge(true)
            }
        } else if (weMuted && ++clearCount >= CLEAR_CHECKS_TO_UNMUTE) {
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

    // Small pink "Ad muted" tag in the top-right corner while an ad is silenced
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
                gravity = Gravity.TOP or Gravity.END
                x = (32 * d).toInt()
                y = (28 * d).toInt()
            }
            wm.addView(tv, lp)
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
