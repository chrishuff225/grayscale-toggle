package com.chrishuff.grayscale

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Fires when a pause timer ends, restoring grayscale. Also exposes helpers to
 * start and cancel a pause. The AlarmManager alarm survives the app being closed.
 */
class TimerReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        Prefs.setPausedUntil(context, 0L)
        GrayscaleManager.applyEffectiveState(context, null)
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
            GrayscaleManager.applyEffectiveState(c, null)
            val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, until, pendingIntent(c))
        }

        fun cancelPause(c: Context) {
            Prefs.setPausedUntil(c, 0L)
            val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.cancel(pendingIntent(c))
            GrayscaleManager.applyEffectiveState(c, null)
        }
    }
}
