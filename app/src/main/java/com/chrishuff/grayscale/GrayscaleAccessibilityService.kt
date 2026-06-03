package com.chrishuff.grayscale

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

/**
 * Watches foreground app changes and applies grayscale immediately: excluded apps
 * go to color, everything else goes grayscale. This is the original, simple
 * behavior that works for the large majority of apps.
 *
 * Note: a few apps that dislike a display change mid-launch (e.g. Focus Friend)
 * may close when switched to color. That trade-off is accepted here in favor of
 * reliable exclusions for every other app; such an app can simply be left
 * un-excluded (grayscale) if it misbehaves.
 */
class GrayscaleAccessibilityService : AccessibilityService() {

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
        lastForegroundPackage = pkg
        GrayscaleManager.applyEffectiveState(this, pkg)
    }

    override fun onInterrupt() {}

    override fun onUnbind(intent: Intent?): Boolean {
        isRunning = false
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        isRunning = false
        super.onDestroy()
    }

    companion object {
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
