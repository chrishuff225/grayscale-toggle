package com.chrishuff.grayscale

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

/**
 * Watches foreground app changes and applies grayscale. Excluded apps go to color
 * immediately; everything else goes (back) to grayscale after a short delay.
 *
 * Why the asymmetry:
 *  - Turning grayscale ON writes the system color setting. Doing that the instant a
 *    transition starts (notably the Recents/Overview animation, which the launcher
 *    drives) cancels the transition. Delaying the ON write lets the animation finish.
 *    It is also a no-op whenever the screen is already grayscale, so the delay is only
 *    ever visible when coming from a color (excluded) app.
 *  - Turning grayscale OFF for an excluded app is done immediately so exclusions feel
 *    instant (unless the user opted that app into the per-app delay).
 *
 * Transient windows (keyboard / IME and system UI) and repeat events for the same app
 * are ignored so they never flip an excluded app while you use it.
 */
class GrayscaleAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var pendingApply: Runnable? = null
    private var lastHandledPackage: String? = null

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
        if (pkg == lastHandledPackage) return

        lastHandledPackage = pkg
        lastForegroundPackage = pkg
        cancelPendingApply()

        val desired = GrayscaleManager.desiredState(this, pkg)
        val delayMs = when {
            // Excluded app the user opted into a per-app delay: stay grayscale, then color.
            !desired && Prefs.isDelayed(this, pkg) -> EXCLUDED_DELAY_MS
            // Entering a non-excluded app (turning grayscale back on): delay so we don't
            // write the color setting mid-transition. No-op when already grayscale.
            desired -> TURN_ON_DELAY_MS
            // Entering an excluded app: switch to color immediately.
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
        private const val TURN_ON_DELAY_MS = 400L
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
