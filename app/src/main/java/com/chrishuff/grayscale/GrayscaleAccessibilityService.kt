package com.chrishuff.grayscale

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

/**
 * Watches foreground app changes and applies grayscale immediately: excluded apps
 * go to color, everything else goes grayscale. This is the simple behavior that
 * works for the large majority of apps.
 *
 *  - Transient windows (the on-screen keyboard / IME and the system UI) are ignored
 *    so they don't momentarily flip an excluded app while you use it.
 *  - Excluded apps the user opted into a per-app delay launch in grayscale and are
 *    switched to color a second later, for apps that dislike a mid-launch change.
 */
class GrayscaleAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var pendingApply: Runnable? = null

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

        if (!GrayscaleManager.desiredState(this, pkg) && Prefs.isDelayed(this, pkg)) {
            // Excluded app with the optional per-app delay: let it finish launching in
            // grayscale, then switch to color a second later.
            val runnable = Runnable { GrayscaleManager.applyEffectiveState(this, pkg) }
            pendingApply = runnable
            handler.postDelayed(runnable, EXCLUDED_DELAY_MS)
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
        private const val EXCLUDED_DELAY_MS = 1000L

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
