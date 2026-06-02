package com.smartalarm.data.model

import java.io.Serializable

data class Alarm(
    val id: Int,
    var hour: Int,
    var minute: Int,
    var label: String = "",
    var isEnabled: Boolean = true,
    var repeatDays: Set<Int> = emptySet(), // Calendar.MONDAY etc.
    var ringtonePath: String = "default",
    var vibrate: Boolean = true,
    var targetImagePath: String = "",
    var targetDescription: String = "",
    var snoozeEnabled: Boolean = true,
    var snoozeDuration: Int = 5,
    var volume: Int = 7
) : Serializable {

    fun getTimeString(): String {
        val h = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
        val m = String.format("%02d", minute)
        val amPm = if (hour < 12) "ص" else "م"
        return "$h:$m $amPm"
    }

    fun getTimeString24(): String {
        return String.format("%02d:%02d", hour, minute)
    }

    fun getRepeatText(): String {
        if (repeatDays.isEmpty()) return "مرة واحدة"
        if (repeatDays.size == 7) return "كل يوم"
        val dayNames = mapOf(
            2 to "ح", 3 to "ن", 4 to "ث", 5 to "ر", 6 to "خ", 7 to "ج", 1 to "س"
        )
        return repeatDays.sorted().mapNotNull { dayNames[it] }.joinToString(" ")
    }

    fun hasTarget(): Boolean = targetImagePath.isNotEmpty()
}
