package com.sakura.home

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
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

        private const val LONG_PRESS_MS = 600L

        @Volatile private var instance: HomeButtonService? = null

        /** Force-stops these apps, then returns home. False if the service isn't running. */
        fun forceStop(pkgs: List<String>): Boolean {
            val s = instance ?: return false
            s.closer.stop(pkgs)
            return true
        }

        private val amazonHome = setOf("com.amazon.tv.launcher", "com.amazon.firehomestarter")

        // Panels that float over the current app without replacing it
        private val overlays = setOf("com.amazon.tv.quicksettings.ui", "com.android.systemui")
    }

    private lateinit var adMuter: AdMuter
    private lateinit var closer: AppCloser

    override fun onServiceConnected() {
        super.onServiceConnected()
        adMuter = AdMuter(this)
        closer = AppCloser(this) { goHome() }
        instance = this
        // Take over right after boot, or if Amazon's home is showing (e.g. Sakura was just
        // updated), but never when the service is re-enabled mid-show
        val onAmazonHome = rootInActiveWindow?.packageName?.toString() in amazonHome
        if (SystemClock.elapsedRealtime() < 3 * 60 * 1000 || onAmazonHome) goHome()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        // Our own "Ad muted" tag is a window too; only the home activity counts as leaving HBO Max
        val ownOverlay = pkg == packageName && event.className?.toString() != MainActivity::class.java.name
        if (pkg !in overlays && !ownOverlay) {
            adMuter.setForeground(pkg)
            OpenApps.record(this, pkg)
        }
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

    // Catching the Home button itself means Amazon's home screen never opens, so it can't flash.
    // Amazon's quick-settings panel only accepts Amazon-signed callers, so holding Home opens
    // the full Fire TV settings instead.
    private var homeLongPressed = false

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_HOME) return false
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount == 0) homeLongPressed = false
                // Remotes that repeat a held key get settings while still holding
                if (!homeLongPressed && event.eventTime - event.downTime >= LONG_PRESS_MS) {
                    homeLongPressed = true
                    openSettings()
                }
            }
            KeyEvent.ACTION_UP -> when {
                homeLongPressed -> {}
                event.eventTime - event.downTime >= LONG_PRESS_MS -> openSettings()
                else -> goHome()
            }
        }
        return true
    }

    private fun openSettings() {
        allowAmazonHomeBriefly()
        startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
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
