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
 * Foreground changes are debounced: during a cold app launch the system fires a
 * burst of window events (launcher -> splash -> app -> transient system windows).
 * Writing the system color setting on every one of those caused apps that are
 * sensitive to display changes mid-launch (e.g. Focus Friend) to be killed. We
 * instead wait for the foreground to settle, then write the setting once.
 */
class GrayscaleAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var pendingPackage: String? = null

    private val applyRunnable = Runnable {
        val pkg = pendingPackage
        lastForegroundPackage = pkg
        GrayscaleManager.applyEffectiveState(this, pkg)
    }

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
        // Ignore transient overlays (status bar, shade, volume panel) so they don't
        // flip an excluded app back to grayscale.
        if (IGNORED_PACKAGES.contains(pkg)) return

        pendingPackage = pkg
        handler.removeCallbacks(applyRunnable)
        handler.postDelayed(applyRunnable, DEBOUNCE_MS)
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        isRunning = false
        handler.removeCallbacks(applyRunnable)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        isRunning = false
        handler.removeCallbacks(applyRunnable)
        super.onDestroy()
    }

    companion object {
        /** Delay before reacting to a foreground change, to let launches settle. */
        private const val DEBOUNCE_MS = 700L

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
