package com.chrishuff.grayscale

import android.content.Context

/** Tiny SharedPreferences wrapper holding all persisted state. */
object Prefs {
    private const val NAME = "grayscale_prefs"
    private const val KEY_MASTER = "master_enabled"
    private const val KEY_PAUSED_UNTIL = "paused_until"
    private const val KEY_EXCLUDED = "excluded_packages"
    private const val KEY_DELAYED = "delayed_packages"

    private fun sp(c: Context) = c.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /** Whether the user wants grayscale active in general. */
    fun isMasterEnabled(c: Context): Boolean = sp(c).getBoolean(KEY_MASTER, false)

    fun setMasterEnabled(c: Context, value: Boolean) =
        sp(c).edit().putBoolean(KEY_MASTER, value).apply()

    /** Epoch millis until which grayscale is temporarily paused (0 = not paused). */
    fun getPausedUntil(c: Context): Long = sp(c).getLong(KEY_PAUSED_UNTIL, 0L)

    fun setPausedUntil(c: Context, value: Long) =
        sp(c).edit().putLong(KEY_PAUSED_UNTIL, value).apply()

    /** Package names that should stay in color (never grayscale). */
    fun getExcluded(c: Context): Set<String> =
        sp(c).getStringSet(KEY_EXCLUDED, emptySet())?.toSet() ?: emptySet()

    fun setExcludedFor(c: Context, pkg: String, excluded: Boolean) {
        val current = getExcluded(c).toMutableSet()
        if (excluded) current.add(pkg) else current.remove(pkg)
        sp(c).edit().putStringSet(KEY_EXCLUDED, current).apply()
    }

    /**
     * Excluded packages that should wait briefly after launching before switching to
     * color, for apps that dislike a display change mid-launch.
     */
    fun getDelayed(c: Context): Set<String> =
        sp(c).getStringSet(KEY_DELAYED, emptySet())?.toSet() ?: emptySet()

    fun isDelayed(c: Context, pkg: String): Boolean = getDelayed(c).contains(pkg)

    fun setDelayedFor(c: Context, pkg: String, delayed: Boolean) {
        val current = getDelayed(c).toMutableSet()
        if (delayed) current.add(pkg) else current.remove(pkg)
        sp(c).edit().putStringSet(KEY_DELAYED, current).apply()
    }
}
