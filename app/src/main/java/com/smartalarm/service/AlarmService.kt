package com.smartalarm.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.os.Build
import com.smartalarm.data.prefs.AlarmPreferences
import com.smartalarm.ui.dismiss.DismissActivity
import com.smartalarm.utils.NotificationHelper

class AlarmService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var currentAlarmId: Int = -1
    private val handler = Handler(Looper.getMainLooper())
    private val autoStopRunnable = Runnable { stopAlarm() }

    companion object {
        const val MAX_RING_DURATION_MS = 5 * 60 * 1000L // 5 minutes auto stop
        var isRinging = false

        fun stopAlarm(context: Context) {
            context.stopService(Intent(context, AlarmService::class.java))
        }
    }

    override fun onCreate() {
        super.onCreate()
        NotificationHelper.createChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val alarmId = intent?.getIntExtra("alarm_id", -1) ?: return START_NOT_STICKY
        currentAlarmId = alarmId

        val alarm = AlarmPreferences.getAlarmById(this, alarmId)

        // Start foreground with notification
        val notification = NotificationHelper.buildAlarmNotification(
            this, alarmId, alarm?.label ?: "منبه"
        )
        startForeground(NotificationHelper.NOTIFICATION_ALARM_ID, notification)

        // Launch dismiss screen over lock screen
        val dismissIntent = Intent(this, DismissActivity::class.java).apply {
            putExtra("alarm_id", alarmId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_NO_USER_ACTION
        }
        startActivity(dismissIntent)

        // Start ringing
        isRinging = true
        startRinging(alarm?.ringtonePath, alarm?.volume ?: 7, alarm?.vibrate ?: true)

        // Auto-stop after max duration
        handler.postDelayed(autoStopRunnable, MAX_RING_DURATION_MS)

        return START_STICKY
    }

    private fun startRinging(ringtonePath: String?, volume: Int, vibrate: Boolean) {
        try {
            val ringtoneUri: Uri = when {
                ringtonePath == "default" || ringtonePath.isNullOrEmpty() ->
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                else -> Uri.parse(ringtonePath)
            }

            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val targetVol = (maxVol * volume / 10.0).toInt().coerceIn(1, maxVol)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, targetVol, 0)

            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@AlarmService, ringtoneUri)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            // Fallback to default ringtone
            try {
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .build()
                    )
                    setDataSource(this@AlarmService, uri)
                    isLooping = true
                    prepare()
                    start()
                }
            } catch (_: Exception) {}
        }

        if (vibrate) {
            startVibration()
        }
    }

    private fun startVibration() {
        val pattern = longArrayOf(0, 800, 400, 800, 400, 800, 800)
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(pattern, 0)
        }
    }

    private fun stopAlarm() {
        isRinging = false
        handler.removeCallbacks(autoStopRunnable)
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (_: Exception) {}
        try {
            vibrator?.cancel()
            vibrator = null
        } catch (_: Exception) {}
        NotificationHelper.cancelAlarmNotification(this)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopAlarm()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
