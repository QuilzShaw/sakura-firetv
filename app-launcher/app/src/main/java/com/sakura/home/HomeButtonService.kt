package com.sakura.home

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent

/**
 * Fire OS won't let another app become the default home screen, so this watches for
 * Amazon's home activity coming to the front and immediately opens Sakura Home instead.
 * Also starts Sakura Home at boot, since the system connects this service on startup,
 * and drives [AdMuter] while HBO Max is on screen.
 */
class HomeButtonService : AccessibilityService() {

    companion object {
        // Fire OS hosts its main Settings screen inside Amazon's home activity, so a settings
        // launch looks exactly like "Amazon home appeared". Callers open this window first.
        @Volatile private var allowAmazonUntil = 0L

        fun allowAmazonHomeBriefly() {
            allowAmazonUntil = SystemClock.uptimeMillis() + 5000
        }

        private val amazonHome = setOf("com.amazon.tv.launcher", "com.amazon.firehomestarter")

        // Panels that float over the current app without replacing it
        private val overlays = setOf("com.amazon.tv.quicksettings.ui", "com.android.systemui")
    }

    private lateinit var adMuter: AdMuter

    override fun onServiceConnected() {
        super.onServiceConnected()
        adMuter = AdMuter(this)
        // Only take over right after boot, not when the service is re-enabled mid-show
        if (SystemClock.elapsedRealtime() < 3 * 60 * 1000) goHome()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        // Our own "Ad muted" tag is a window too; only the home activity counts as leaving HBO Max
        val ownOverlay = pkg == packageName && event.className?.toString() != MainActivity::class.java.name
        if (pkg !in overlays && !ownOverlay) adMuter.setForeground(pkg)
        if (pkg !in amazonHome) return

        val cls = event.className?.toString() ?: return
        if (cls.contains("SettingsActivity")) {
            allowAmazonHomeBriefly()
            return
        }
        if (!cls.contains("HomeActivity") && !cls.contains("HomeStarterActivity")) return
        if (SystemClock.uptimeMillis() < allowAmazonUntil) return
        goHome()
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        adMuter.shutdown()
        return super.onUnbind(intent)
    }

    private fun goHome() {
        startActivity(
            Intent(this, MainActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            )
        )
    }
}
