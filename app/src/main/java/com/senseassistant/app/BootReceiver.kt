package com.senseassistant.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        val alarmManager = context.getSystemService(
            Context.ALARM_SERVICE
        ) as AlarmManager

        val reminders = ReminderStore.getAll(context)

        for (reminder in reminders) {
            if (reminder.timeMillis <= System.currentTimeMillis()) {
                continue
            }

            val alarmIntent = Intent(
                context,
                ReminderReceiver::class.java
            ).apply {
                putExtra("message", reminder.message)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                reminder.id.toInt(),
                alarmIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
            )

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    !alarmManager.canScheduleExactAlarms()
                ) {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        reminder.timeMillis,
                        pendingIntent
                    )
                } else {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        reminder.timeMillis,
                        pendingIntent
                    )
                }
            } catch (_: SecurityException) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    reminder.timeMillis,
                    pendingIntent
                )
            }
        }
    }
}
