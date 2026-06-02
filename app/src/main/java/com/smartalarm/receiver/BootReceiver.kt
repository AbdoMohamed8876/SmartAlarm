package com.smartalarm.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.smartalarm.data.prefs.AlarmPreferences
import com.smartalarm.utils.AlarmScheduler

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.LOCKED_BOOT_COMPLETED") return

        val alarms = AlarmPreferences.loadAlarms(context)
        AlarmScheduler.rescheduleAll(context, alarms)
    }
}
