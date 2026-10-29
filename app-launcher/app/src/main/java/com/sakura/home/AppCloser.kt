package com.sakura.home

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView

/**
 * Closes apps the way you would by hand: opens each app's info page in Fire TV settings and
 * presses Force stop. Fire OS ignores the standard "close background app" call from other
 * apps, but Force stop always works. A full-screen sakura cover hides the settings pages.
 */
class AppCloser(private val service: AccessibilityService, private val onDone: () -> Unit) {

    companion object {
        private const val SETTINGS = "com.amazon.tv.settings.v2"
        private const val ACTION_ID = "$SETTINGS:id/action_title"
        private const val TITLE_ID = "$SETTINGS:id/left_pane_title"
        private const val RETRY_MS = 250L
        private const val MAX_TRIES = 40 // ~10 s: Settings is slow to open the first time
    }

    private val handler = Handler(Looper.getMainLooper())
    private val queue = ArrayDeque<String>()
    private var current: String? = null
    private var tries = 0
    private var cover: TextView? = null

    val busy get() = current != null || queue.isNotEmpty()

    fun stop(pkgs: List<String>) {
        pkgs.filter { it != current && it !in queue }.forEach { queue.addLast(it) }
        if (current == null) next()
    }

    private fun next() {
        val pkg = queue.removeFirstOrNull()
        if (pkg == null) {
            current = null
            handler.postDelayed({ showCover(null); onDone() }, 150)
            return
        }
        current = pkg
        tries = 0
        showCover("✿  Closing ${labelOf(pkg)}…")
        // A fresh task each time: Settings otherwise reuses the page of the last app it showed
        service.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION
            )
        )
        handler.postDelayed(::pressForceStop, RETRY_MS)
    }

    private fun pressForceStop() {
        if (findForceStop() == null) {
            // Page not up yet; give up on this app after a few seconds rather than hang
            if (++tries < MAX_TRIES) handler.postDelayed(::pressForceStop, RETRY_MS) else next()
            return
        }
        // Some apps' pages add rows (e.g. Permissions) just after opening, which rebuilds the
        // list, so let it settle and look the row up again before each step
        handler.postDelayed({
            val row = findForceStop() ?: return@postDelayed pressForceStop()
            // The list only acts on its selected row: select Force stop, then press it
            row.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            handler.postDelayed({
                (findForceStop() ?: row).performAction(AccessibilityNodeInfo.ACTION_CLICK)
                handler.postDelayed(::next, 400)
            }, 250)
        }, 400)
    }

    /** The clickable Force stop row, once the page shows this app (never a stale page for another). */
    private fun findForceStop(): AccessibilityNodeInfo? {
        val root = service.rootInActiveWindow?.takeIf { it.packageName == SETTINGS } ?: return null
        val pageFor = root.findAccessibilityNodeInfosByViewId(TITLE_ID).firstOrNull()?.text?.toString()
        if (pageFor != labelOf(current ?: "")) return null
        val button = root.findAccessibilityNodeInfosByViewId(ACTION_ID)
            .firstOrNull { it.text?.toString() == "Force stop" } ?: return null
        var row: AccessibilityNodeInfo? = button
        while (row != null && !row.isClickable) row = row.parent
        return row ?: button
    }

    private fun labelOf(pkg: String): String = try {
        val pm = service.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) { "app" }

    private fun showCover(text: String?) {
        val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        if (text == null) {
            cover?.let { v -> runCatching { wm.removeView(v) } }
            cover = null
            return
        }
        cover?.let { it.text = text; return }
        val tv = TextView(service).apply {
            this.text = text
            setTextColor(Color.parseColor("#FFB7CF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#1E1226"))
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            // Not focusable, so the settings page underneath stays the active window we read
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.OPAQUE,
        )
        wm.addView(tv, lp)
        cover = tv
    }
}
