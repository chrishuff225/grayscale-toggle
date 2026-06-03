package com.chrishuff.grayscale

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Reads and writes the system "monochromacy" daltonizer setting, which turns the
 * entire screen grayscale. Writing requires WRITE_SECURE_SETTINGS, granted once
 * over ADB.
 */
object GrayscaleManager {
    private const val DALTONIZER_ENABLED = "accessibility_display_daltonizer_enabled"
    private const val DALTONIZER = "accessibility_display_daltonizer"
    private const val MODE_MONOCHROME = 0

    fun hasPermission(c: Context): Boolean =
        ContextCompat.checkSelfPermission(c, Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

    fun isGrayscaleOn(c: Context): Boolean =
        Settings.Secure.getInt(c.contentResolver, DALTONIZER_ENABLED, 0) == 1

    fun setGrayscale(c: Context, on: Boolean): Boolean {
        if (!hasPermission(c)) return false
        return try {
            val cr = c.contentResolver
            if (on) {
                Settings.Secure.putInt(cr, DALTONIZER, MODE_MONOCHROME)
                Settings.Secure.putInt(cr, DALTONIZER_ENABLED, 1)
            } else {
                Settings.Secure.putInt(cr, DALTONIZER_ENABLED, 0)
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * The grayscale state we want right now, given saved prefs and the current
     * foreground app. Off if disabled, paused, or the foreground app is excluded.
     */
    fun desiredState(c: Context, foregroundPackage: String?): Boolean {
        if (!Prefs.isMasterEnabled(c)) return false
        if (Prefs.getPausedUntil(c) > System.currentTimeMillis()) return false
        if (foregroundPackage != null && Prefs.getExcluded(c).contains(foregroundPackage)) return false
        return true
    }

    /** Apply the desired state, but only write when it actually differs. */
    fun applyEffectiveState(c: Context, foregroundPackage: String?) {
        val desired = desiredState(c, foregroundPackage)
        if (isGrayscaleOn(c) != desired) {
            setGrayscale(c, desired)
        }
    }
}
