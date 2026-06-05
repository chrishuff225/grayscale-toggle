package com.chrishuff.grayscale

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

/**
 * Watches foreground app changes and applies grayscale: excluded apps go to color,
 * other apps go to grayscale.
 *
 * Grayscale is only ever changed for real launchable ("drawer") apps. The home
 * launcher — which also hosts the Recents/Overview animation — has no drawer icon,
 * so it (and Recents, system UI and the keyboard) is ignored. That avoids writing
 * the system color setting mid-transition, which was cancelling Recents.
 */
class GrayscaleAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var pendingApply: Runnable? = null
    private var lastHandledPackage: String? = null

    private var managed: Set<String> = emptySet()
    private var managedAt: Long = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
        managedPackages() // warm the cache
        GrayscaleManager.applyEffectiveState(this, lastForegroundPackage)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString()
        if (pkg.isNullOrEmpty()) return
        if (pkg == packageName) return                 // our own app
        if (pkg == currentImePackage()) return         // on-screen keyboard
        if (!managedPackages().contains(pkg)) return   // launcher / Recents / system surfaces
        if (pkg == lastHandledPackage) return          // repeat events for the same app

        lastHandledPackage = pkg
        lastForegroundPackage = pkg
        cancelPendingApply()

        if (!GrayscaleManager.desiredState(this, pkg) && Prefs.isDelayed(this, pkg)) {
            // Excluded app the user opted into a per-app delay: stay grayscale, then color.
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

    private fun currentImePackage(): String? =
        Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
            ?.substringBefore('/')

    /** Packages that have a launcher (app-drawer) icon — i.e. real, openable apps. */
    private fun managedPackages(): Set<String> {
        val now = System.currentTimeMillis()
        if (managedAt == 0L || now - managedAt > REFRESH_MS) {
            val computed = try {
                val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                packageManager.queryIntentActivities(intent, 0)
                    .mapNotNull { it.activityInfo?.packageName }
                    .toHashSet()
            } catch (e: Exception) {
                null
            }
            if (!computed.isNullOrEmpty()) {
                managed = computed
                managedAt = now
            }
        }
        return managed
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
        private const val EXCLUDED_DELAY_MS = 5000L
        private const val REFRESH_MS = 120_000L

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
