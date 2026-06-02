package com.smartalarm.data.prefs

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.smartalarm.data.model.Alarm

object AlarmPreferences {

    private const val PREFS_NAME = "smart_alarm_prefs"
    private const val KEY_ALARMS = "alarms_list"
    private const val KEY_GEMINI_API = "gemini_api_key"
    private const val KEY_NEXT_ID = "next_alarm_id"

    private val gson = Gson()

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveAlarms(context: Context, alarms: List<Alarm>) {
        val json = gson.toJson(alarms)
        getPrefs(context).edit().putString(KEY_ALARMS, json).apply()
    }

    fun loadAlarms(context: Context): MutableList<Alarm> {
        val json = getPrefs(context).getString(KEY_ALARMS, null) ?: return mutableListOf()
        return try {
            val type = object : TypeToken<MutableList<Alarm>>() {}.type
            gson.fromJson(json, type) ?: mutableListOf()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    fun getAlarmById(context: Context, id: Int): Alarm? {
        return loadAlarms(context).find { it.id == id }
    }

    fun saveAlarm(context: Context, alarm: Alarm) {
        val alarms = loadAlarms(context)
        val index = alarms.indexOfFirst { it.id == alarm.id }
        if (index >= 0) {
            alarms[index] = alarm
        } else {
            alarms.add(alarm)
        }
        saveAlarms(context, alarms)
    }

    fun deleteAlarm(context: Context, alarmId: Int) {
        val alarms = loadAlarms(context).filter { it.id != alarmId }.toMutableList()
        saveAlarms(context, alarms)
    }

    fun getNextId(context: Context): Int {
        val prefs = getPrefs(context)
        val next = prefs.getInt(KEY_NEXT_ID, 1)
        prefs.edit().putInt(KEY_NEXT_ID, next + 1).apply()
        return next
    }

    fun saveGeminiApiKey(context: Context, key: String) {
        getPrefs(context).edit().putString(KEY_GEMINI_API, key).apply()
    }

    fun getGeminiApiKey(context: Context): String {
        return getPrefs(context).getString(KEY_GEMINI_API, "") ?: ""
    }
}
