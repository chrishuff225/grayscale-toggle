package com.chrishuff.grayscale

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * Watches foreground app changes so excluded apps stay in color while everything
 * else is grayscale. The service keeps running in the background once enabled.
 */
class GrayscaleAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        GrayscaleManager.applyEffectiveState(this, null)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString()
        if (pkg.isNullOrEmpty()) return
        GrayscaleManager.applyEffectiveState(this, pkg)
    }

    override fun onInterrupt() {}
}
