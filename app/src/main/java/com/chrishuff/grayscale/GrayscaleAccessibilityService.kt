package com.chrishuff.grayscale

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent

/**
 * Watches foreground app changes so excluded apps stay in color while everything
 * else is grayscale. The service keeps running in the background once enabled.
 *
 * Two subtleties handled here:
 *  - Repeat window events for the same app are ignored. Some apps (e.g. a focus
 *    timer) fire window-state events constantly; without this guard a debounce
 *    would reset forever and the toggle would never apply.
 *  - Turning grayscale OFF for an excluded app is delayed briefly. Writing the
 *    system color setting in the middle of an app's cold launch could kill apps
 *    that are sensitive to display changes (e.g. Focus Friend), so we wait for
 *    the launch to settle first. Turning grayscale back ON is done immediately.
 */
class GrayscaleAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var currentPackage: String? = null

    private val applyExcludedRunnable = Runnable {
        GrayscaleManager.applyEffectiveState(this, currentPackage)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
        GrayscaleManager.applyEffectiveState(this, currentPackage)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString()
        if (pkg.isNullOrEmpty()) return
        // Transient overlays (status bar, shade, volume panel) are not a real
        // foreground app, so they must not flip an excluded app back to grayscale.
        if (IGNORED_PACKAGES.contains(pkg)) return
        // Same app still in front: ignore repeat events so a pending toggle isn't
        // reset over and over by apps that emit frequent window events.
        if (pkg == currentPackage) return

        currentPackage = pkg
        lastForegroundPackage = pkg
        handler.removeCallbacks(applyExcludedRunnable)

        if (GrayscaleManager.desiredState(this, pkg)) {
            // Entering a non-excluded app: restore grayscale right away.
            GrayscaleManager.applyEffectiveState(this, pkg)
        } else {
            // Entering an excluded app: wait for it to finish launching before
            // switching the screen to color, so we don't interrupt it.
            handler.postDelayed(applyExcludedRunnable, EXCLUDED_APP_DELAY_MS)
        }
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        isRunning = false
        handler.removeCallbacks(applyExcludedRunnable)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        isRunning = false
        handler.removeCallbacks(applyExcludedRunnable)
        super.onDestroy()
    }

    companion object {
        /** Delay before switching an excluded app to color, to let its launch settle. */
        private const val EXCLUDED_APP_DELAY_MS = 1000L

        private val IGNORED_PACKAGES = setOf("com.android.systemui")

        /** True while the system has this accessibility service bound and running. */
        @Volatile
        var isRunning: Boolean = false
            private set

        /** The most recent real foreground app package, used by the pause timer. */
        @Volatile
        var lastForegroundPackage: String? = null
            private set
    }
}
