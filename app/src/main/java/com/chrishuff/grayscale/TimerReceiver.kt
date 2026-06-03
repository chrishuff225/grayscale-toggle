package com.chrishuff.grayscale

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Fires when a pause timer ends, restoring grayscale. Also exposes helpers to
 * start and cancel a pause. The AlarmManager alarm survives the app being closed.
 */
class TimerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        Prefs.setPausedUntil(context, 0L)
        // Restore grayscale for whatever app is in front now, honoring exclusions.
        GrayscaleManager.applyEffectiveState(context, GrayscaleAccessibilityService.lastForegroundPackage)
    }

    companion object {
        private const val REQUEST_CODE = 1001

        private fun pendingIntent(c: Context): PendingIntent {
            val intent = Intent(c, TimerReceiver::class.java)
            return PendingIntent.getBroadcast(
                c, REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        fun startPause(c: Context, durationMillis: Long) {
            val until = System.currentTimeMillis() + durationMillis
            Prefs.setPausedUntil(c, until)
            GrayscaleManager.applyEffectiveState(c, GrayscaleAccessibilityService.lastForegroundPackage)
            scheduleAlarm(c, until)
        }

        private fun scheduleAlarm(c: Context, until: Long) {
            val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = pendingIntent(c)
            try {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, until, pi)
                } else {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, until, pi)
                }
            } catch (e: SecurityException) {
                // Exact-alarm permission missing: fall back to an inexact alarm.
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, until, pi)
            }
        }

        fun cancelPause(c: Context) {
            Prefs.setPausedUntil(c, 0L)
            val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.cancel(pendingIntent(c))
            GrayscaleManager.applyEffectiveState(c, GrayscaleAccessibilityService.lastForegroundPackage)
        }
    }
}
