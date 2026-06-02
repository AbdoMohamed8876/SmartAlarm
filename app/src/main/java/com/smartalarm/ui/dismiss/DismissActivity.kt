package com.smartalarm.ui.dismiss

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.smartalarm.R
import com.smartalarm.ai.GeminiVerifier
import com.smartalarm.data.prefs.AlarmPreferences
import com.smartalarm.databinding.ActivityDismissBinding
import com.smartalarm.service.AlarmService
import com.smartalarm.utils.ImageUtils
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class DismissActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDismissBinding
    private var alarmId: Int = -1
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    private var capturedImagePath: String? = null
    private var isVerifying = false
    private val handler = Handler(Looper.getMainLooper())

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera()
        else binding.tvCameraStatus.text = "يحتاج إذن الكاميرا لإيقاف المنبه"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show over lock screen
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }

        binding = ActivityDismissBinding.inflate(layoutInflater)
        setContentView(binding.root)

        alarmId = intent.getIntExtra("alarm_id", -1)
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Disable back button
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Do nothing - can't back out without dismissing
            }
        })

        setupUI()
        loadAlarmInfo()

        val apiKey = AlarmPreferences.getGeminiApiKey(this)
        val alarm = AlarmPreferences.getAlarmById(this, alarmId)

        if (alarm?.hasTarget() == true && apiKey.isNotEmpty()) {
            // Needs photo verification
            showCameraMode()
            checkCameraPermission()
        } else {
            // No target set - show simple dismiss
            showSimpleDismiss()
        }
    }

    private fun setupUI() {
        binding.btnSnooze.setOnClickListener { snoozeAlarm() }
        binding.btnCaptureAndDismiss.setOnClickListener {
            if (!isVerifying) captureAndVerify()
        }
        binding.btnSimpleDismiss.setOnClickListener { dismissAlarm() }
    }

    private fun loadAlarmInfo() {
        val alarm = AlarmPreferences.getAlarmById(this, alarmId)
        alarm?.let {
            binding.tvAlarmTime.text = it.getTimeString()
            binding.tvAlarmLabel.text = it.label.ifEmpty { "منبه" }
            if (it.hasTarget()) {
                binding.tvTargetHint.text = "📸 لإيقاف المنبه، صوّر:\n${it.targetDescription}"
            }
        }
    }

    private fun showCameraMode() {
        binding.layoutCamera.visibility = View.VISIBLE
        binding.layoutSimple.visibility = View.GONE
        binding.btnCaptureAndDismiss.visibility = View.VISIBLE
        binding.btnSnooze.visibility = View.VISIBLE
    }

    private fun showSimpleDismiss() {
        binding.layoutCamera.visibility = View.GONE
        binding.layoutSimple.visibility = View.VISIBLE
        binding.btnCaptureAndDismiss.visibility = View.GONE
    }

    private fun checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            permLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            try {
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
            } catch (e: Exception) {
                binding.tvCameraStatus.text = "خطأ في تشغيل الكاميرا"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun captureAndVerify() {
        val capture = imageCapture ?: return
        isVerifying = true
        binding.btnCaptureAndDismiss.isEnabled = false
        binding.btnCaptureAndDismiss.text = "جاري التحقق..."
        binding.progressVerify.visibility = View.VISIBLE

        val photoFile = File(
            cacheDir,
            "dismiss_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.jpg"
        )

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    capturedImagePath = photoFile.absolutePath
                    verifyWithAI(photoFile.absolutePath)
                }

                override fun onError(exception: ImageCaptureException) {
                    isVerifying = false
                    binding.btnCaptureAndDismiss.isEnabled = true
                    binding.btnCaptureAndDismiss.text = "📸 التقط صورة لإيقاف المنبه"
                    binding.progressVerify.visibility = View.GONE
                    Toast.makeText(this@DismissActivity, "خطأ في التقاط الصورة", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun verifyWithAI(photoPath: String) {
        val alarm = AlarmPreferences.getAlarmById(this, alarmId) ?: run {
            dismissAlarm()
            return
        }
        val apiKey = AlarmPreferences.getGeminiApiKey(this)
        if (apiKey.isEmpty()) { dismissAlarm(); return }

        lifecycleScope.launch {
            try {
                val verifier = GeminiVerifier(apiKey)
                val liveBase64 = ImageUtils.fileToBase64(photoPath)

                val result = if (alarm.targetImagePath.isNotEmpty()) {
                    val targetBase64 = ImageUtils.fileToBase64(alarm.targetImagePath)
                    verifier.verifyMatch(targetBase64, liveBase64, alarm.targetDescription)
                } else {
                    verifier.quickVerify(liveBase64, alarm.targetDescription)
                }

                runOnUiThread {
                    binding.progressVerify.visibility = View.GONE
                    isVerifying = false

                    if (result.isMatch || result.confidence >= 0.6f) {
                        binding.tvVerifyResult.text = "✅ تم التحقق! ${result.reason}"
                        binding.tvVerifyResult.visibility = View.VISIBLE
                        handler.postDelayed({ dismissAlarm() }, 1200)
                    } else {
                        binding.tvVerifyResult.text = "❌ لم يتم التعرف على الهدف\n${result.reason}\nحاول مجدداً"
                        binding.tvVerifyResult.visibility = View.VISIBLE
                        binding.btnCaptureAndDismiss.isEnabled = true
                        binding.btnCaptureAndDismiss.text = "📸 حاول مجدداً"
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    binding.progressVerify.visibility = View.GONE
                    isVerifying = false
                    binding.btnCaptureAndDismiss.isEnabled = true
                    binding.btnCaptureAndDismiss.text = "📸 التقط صورة لإيقاف المنبه"
                    binding.tvVerifyResult.text = "خطأ في الاتصال - تحقق من الإنترنت"
                    binding.tvVerifyResult.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun dismissAlarm() {
        AlarmService.stopAlarm(this)
        // Reschedule if repeating
        val alarm = AlarmPreferences.getAlarmById(this, alarmId)
        if (alarm != null && alarm.repeatDays.isNotEmpty()) {
            AlarmPreferences.saveAlarm(this, alarm)
            val updatedAlarm = alarm.copy(isEnabled = true)
            AlarmPreferences.saveAlarm(this, updatedAlarm)
        } else if (alarm != null && alarm.repeatDays.isEmpty()) {
            val updated = alarm.copy(isEnabled = false)
            AlarmPreferences.saveAlarm(this, updated)
        }
        cameraExecutor.shutdown()
        finish()
    }

    private fun snoozeAlarm() {
        val alarm = AlarmPreferences.getAlarmById(this, alarmId)
        val snoozeMins = alarm?.snoozeDuration ?: 5
        // Create a one-time alarm for snooze
        val snoozeAlarm = alarm?.copy(
            isEnabled = true,
            repeatDays = emptySet()
        )?.let {
            val cal = java.util.Calendar.getInstance()
            cal.add(java.util.Calendar.MINUTE, snoozeMins)
            it.copy(hour = cal.get(java.util.Calendar.HOUR_OF_DAY), minute = cal.get(java.util.Calendar.MINUTE))
        }
        snoozeAlarm?.let {
            val tempId = alarmId + 10000
            val temp = it.copy(id = tempId)
            AlarmPreferences.saveAlarm(this, temp)
            com.smartalarm.utils.AlarmScheduler.scheduleAlarm(this, temp)
        }
        Toast.makeText(this, "تم تأجيل المنبه $snoozeMins دقائق", Toast.LENGTH_SHORT).show()
        AlarmService.stopAlarm(this)
        cameraExecutor.shutdown()
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
