package com.chrishuff.grayscale

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

/**
 * Watches foreground app changes and applies grayscale immediately: excluded apps
 * go to color, everything else goes grayscale. This is the simple behavior that
 * works for the large majority of apps.
 *
 * Transient windows are ignored so they don't momentarily flip an excluded app:
 *  - the on-screen keyboard (IME) — opening it inside an excluded app was flipping
 *    the screen to grayscale and blocking input;
 *  - the system UI (status bar, notification shade, volume panel).
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
        if (isTransientWindow(pkg)) return
        lastForegroundPackage = pkg
        GrayscaleManager.applyEffectiveState(this, pkg)
    }

    /** Keyboard and system windows are not a real foreground app change. */
    private fun isTransientWindow(pkg: String): Boolean {
        if (pkg == SYSTEM_UI_PACKAGE) return true
        val ime = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        val imePackage = ime?.substringBefore('/')
        return pkg == imePackage
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
        private const val SYSTEM_UI_PACKAGE = "com.android.systemui"

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
