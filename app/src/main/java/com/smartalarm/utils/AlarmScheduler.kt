package com.smartalarm.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.smartalarm.data.model.Alarm
import com.smartalarm.receiver.AlarmReceiver
import java.util.Calendar

object AlarmScheduler {

    fun scheduleAlarm(context: Context, alarm: Alarm) {
        if (!alarm.isEnabled) return
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = createIntent(context, alarm)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerTime = getNextTriggerTime(alarm)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(triggerTime, pendingIntent),
                    pendingIntent
                )
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent
                )
            }
        } else {
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(triggerTime, pendingIntent),
                pendingIntent
            )
        }
    }

    fun cancelAlarm(context: Context, alarm: Alarm) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = createIntent(context, alarm)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            alarm.id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    fun rescheduleAll(context: Context, alarms: List<Alarm>) {
        alarms.filter { it.isEnabled }.forEach { scheduleAlarm(context, it) }
    }

    private fun createIntent(context: Context, alarm: Alarm): Intent {
        return Intent(context, AlarmReceiver::class.java).apply {
            action = "com.smartalarm.ACTION_ALARM_TRIGGER"
            putExtra("alarm_id", alarm.id)
            putExtra("alarm_label", alarm.label)
        }
    }

    private fun getNextTriggerTime(alarm: Alarm): Long {
        val now = Calendar.getInstance()
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, alarm.hour)
            set(Calendar.MINUTE, alarm.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (alarm.repeatDays.isEmpty()) {
            if (cal.timeInMillis <= now.timeInMillis) {
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
            return cal.timeInMillis
        }
        // Find next occurrence for repeating alarm
        var daysAhead = 0
        while (daysAhead < 8) {
            val checkCal = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, daysAhead)
                set(Calendar.HOUR_OF_DAY, alarm.hour)
                set(Calendar.MINUTE, alarm.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val dayOfWeek = checkCal.get(Calendar.DAY_OF_WEEK)
            if (alarm.repeatDays.contains(dayOfWeek) && checkCal.timeInMillis > now.timeInMillis) {
                return checkCal.timeInMillis
            }
            daysAhead++
        }
        return cal.timeInMillis
    }

    fun getTimeUntilAlarm(alarm: Alarm): String {
        val now = System.currentTimeMillis()
        val trigger = getNextTriggerTime(alarm)
        val diff = trigger - now
        val hours = diff / (1000 * 60 * 60)
        val minutes = (diff % (1000 * 60 * 60)) / (1000 * 60)
        return when {
            hours > 0 -> "بعد ${hours}س ${minutes}د"
            minutes > 0 -> "بعد ${minutes} دقيقة"
            else -> "الآن"
        }
    }
}
