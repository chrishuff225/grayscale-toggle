package com.chrishuff.grayscale

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

/**
 * Watches foreground app changes and applies grayscale immediately: excluded apps
 * go to color, everything else goes grayscale. This is the simple behavior that
 * works for the large majority of apps.
 *
 * Some transitions need special handling because writing the system color setting
 * mid-animation interferes with them:
 *  - Transient windows (keyboard / IME and system UI) are ignored entirely.
 *  - The launcher also hosts the Recents/Overview animation, so the switch is
 *    delayed briefly when entering it (otherwise Recents fails to open).
 *  - Excluded apps the user opted into a per-app delay launch in grayscale and are
 *    switched to color after a few seconds.
 */
class GrayscaleAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var pendingApply: Runnable? = null
    private var homePackage: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
        GrayscaleManager.applyEffectiveState(this, lastForegroundPackage)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString()
        if (pkg.isNullOrEmpty()) return
        if (isTransientWindow(pkg)) return

        lastForegroundPackage = pkg
        cancelPendingApply()

        val delayMs = when {
            // Entering the launcher (which also drives the Recents/Overview animation):
            // wait for the transition to settle before touching the color setting.
            isHome(pkg) -> LAUNCHER_DELAY_MS
            // Excluded apps the user opted into a per-app delay.
            !GrayscaleManager.desiredState(this, pkg) && Prefs.isDelayed(this, pkg) -> EXCLUDED_DELAY_MS
            else -> 0L
        }

        if (delayMs > 0L) {
            val runnable = Runnable { GrayscaleManager.applyEffectiveState(this, pkg) }
            pendingApply = runnable
            handler.postDelayed(runnable, delayMs)
        } else {
            GrayscaleManager.applyEffectiveState(this, pkg)
        }
    }

    private fun cancelPendingApply() {
        pendingApply?.let { handler.removeCallbacks(it) }
        pendingApply = null
    }

    /** Keyboard and system windows are not a real foreground app change. */
    private fun isTransientWindow(pkg: String): Boolean {
        if (pkg == SYSTEM_UI_PACKAGE) return true
        val ime = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        return pkg == ime?.substringBefore('/')
    }

    private fun isHome(pkg: String): Boolean {
        if (homePackage == null) {
            homePackage = try {
                val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                    ?.activityInfo?.packageName
            } catch (e: Exception) {
                null
            }
        }
        return pkg == homePackage
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        isRunning = false
        cancelPendingApply()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        isRunning = false
        cancelPendingApply()
        super.onDestroy()
    }

    companion object {
        private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
        private const val LAUNCHER_DELAY_MS = 700L
        private const val EXCLUDED_DELAY_MS = 5000L

        /** True while the system has this accessibility service bound and running. */
        @Volatile
        var isRunning: Boolean = false
            private set

        /** The most recent foreground app package, used by the pause timer. */
        @Volatile
        var lastForegroundPackage: String? = null
            private set
    }
}
