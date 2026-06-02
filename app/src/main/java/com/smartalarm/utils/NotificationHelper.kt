package com.smartalarm.utils

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.smartalarm.R
import com.smartalarm.ui.dismiss.DismissActivity

object NotificationHelper {

    const val CHANNEL_ALARM = "smart_alarm_channel"
    const val CHANNEL_SILENT = "smart_alarm_silent"
    const val NOTIFICATION_ALARM_ID = 1001
    const val NOTIFICATION_SERVICE_ID = 1002

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val alarmChannel = NotificationChannel(
                CHANNEL_ALARM,
                "تنبيهات المنبه",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "إشعارات المنبه الذكي"
                enableVibration(true)
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            val silentChannel = NotificationChannel(
                CHANNEL_SILENT,
                "خدمة المنبه",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "الخدمة الخلفية للمنبه"
                setShowBadge(false)
            }

            nm.createNotificationChannel(alarmChannel)
            nm.createNotificationChannel(silentChannel)
        }
    }

    fun buildAlarmNotification(context: Context, alarmId: Int, label: String): Notification {
        val dismissIntent = Intent(context, DismissActivity::class.java).apply {
            putExtra("alarm_id", alarmId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context, alarmId, dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, CHANNEL_ALARM)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle("⏰ ${label.ifEmpty { "المنبه" }}")
            .setContentText("اضغط لإيقاف المنبه")
            .setFullScreenIntent(pi, true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(false)
            .setOngoing(true)
            .build()
    }

    fun buildServiceNotification(context: Context): Notification {
        return NotificationCompat.Builder(context, CHANNEL_SILENT)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle("Smart Alarm")
            .setContentText("المنبه الذكي يعمل")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    fun cancelAlarmNotification(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIFICATION_ALARM_ID)
    }
}
