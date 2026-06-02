package com.smartalarm.ui.setup

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.smartalarm.R
import com.smartalarm.ai.GeminiVerifier
import com.smartalarm.data.prefs.AlarmPreferences
import com.smartalarm.databinding.ActivitySetupTargetBinding
import com.smartalarm.utils.ImageUtils
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class SetupTargetActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySetupTargetBinding
    private var alarmId: Int = -1
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    private var capturedPhotoPath: String? = null
    private var capturedDescription: String? = null

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera() else {
            Toast.makeText(this, "يحتاج إذن الكاميرا", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySetupTargetBinding.inflate(layoutInflater)
        setContentView(binding.root)

        alarmId = intent.getIntExtra("alarm_id", -1)
        cameraExecutor = Executors.newSingleThreadExecutor()

        supportActionBar?.apply {
            title = "تحديد هدف المنبه"
            setDisplayHomeAsUpEnabled(true)
        }

        setupButtons()
        showStep1()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            permLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun setupButtons() {
        binding.btnCapture.setOnClickListener { capturePhoto() }
        binding.btnRetake.setOnClickListener { retakePhoto() }
        binding.btnConfirmTarget.setOnClickListener { confirmAndSave() }
        binding.btnClearTarget.setOnClickListener { clearTarget() }
    }

    private fun showStep1() {
        binding.layoutPreview.visibility = View.VISIBLE
        binding.layoutConfirm.visibility = View.GONE
        binding.tvInstruction.text = "📸 صوّر الشيء الذي يجب أن تذهب إليه لإيقاف المنبه\n(مثلاً: التلفزيون، الباب الرئيسي، المطبخ)"
    }

    private fun showStep2(description: String) {
        binding.layoutConfirm.visibility = View.VISIBLE
        binding.layoutPreview.visibility = View.GONE
        binding.tvDetectedObject.text = "🤖 الذكاء الاصطناعي رأى:\n$description"
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()
            try {
                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
            } catch (e: Exception) {
                Toast.makeText(this, "خطأ في الكاميرا", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun capturePhoto() {
        val capture = imageCapture ?: return
        binding.btnCapture.isEnabled = false
        binding.progressAnalyze.visibility = View.VISIBLE
        binding.tvInstruction.text = "🔍 جاري التحليل بالذكاء الاصطناعي..."

        val photoFile = File(cacheDir, "target_${System.currentTimeMillis()}.jpg")
        val options = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        capture.takePicture(
            options,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    analyzePhoto(photoFile.absolutePath)
                }
                override fun onError(e: ImageCaptureException) {
                    binding.btnCapture.isEnabled = true
                    binding.progressAnalyze.visibility = View.GONE
                    binding.tvInstruction.text = "خطأ في التقاط الصورة، حاول مجدداً"
                }
            }
        )
    }

    private fun analyzePhoto(path: String) {
        val apiKey = AlarmPreferences.getGeminiApiKey(this)
        if (apiKey.isEmpty()) {
            // No API key - save without description
            capturedPhotoPath = path
            capturedDescription = "صورة مخصصة"
            binding.progressAnalyze.visibility = View.GONE
            showStep2(capturedDescription!!)
            return
        }

        lifecycleScope.launch {
            try {
                val verifier = GeminiVerifier(apiKey)
                val base64 = ImageUtils.fileToBase64(path)
                val description = verifier.describeTarget(base64)
                capturedPhotoPath = path
                capturedDescription = description
                runOnUiThread {
                    binding.progressAnalyze.visibility = View.GONE
                    binding.btnCapture.isEnabled = true
                    showStep2(description)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    capturedPhotoPath = path
                    capturedDescription = "هدف مخصص"
                    binding.progressAnalyze.visibility = View.GONE
                    binding.btnCapture.isEnabled = true
                    showStep2(capturedDescription!!)
                }
            }
        }
    }

    private fun retakePhoto() {
        capturedPhotoPath = null
        capturedDescription = null
        showStep1()
    }

    private fun confirmAndSave() {
        val photoPath = capturedPhotoPath ?: return
        val description = capturedDescription ?: "هدف مخصص"
        val alarm = AlarmPreferences.getAlarmById(this, alarmId) ?: return

        // Copy photo to permanent storage
        val savedPath = ImageUtils.copyFileToInternal(this, photoPath, "target_$alarmId.jpg")

        // Delete old target image if exists
        if (alarm.targetImagePath.isNotEmpty() && alarm.targetImagePath != savedPath) {
            ImageUtils.deleteFile(alarm.targetImagePath)
        }

        val updated = alarm.copy(
            targetImagePath = savedPath,
            targetDescription = description
        )
        AlarmPreferences.saveAlarm(this, updated)
        Toast.makeText(this, "✅ تم حفظ الهدف بنجاح!", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun clearTarget() {
        val alarm = AlarmPreferences.getAlarmById(this, alarmId) ?: return
        if (alarm.targetImagePath.isNotEmpty()) {
            ImageUtils.deleteFile(alarm.targetImagePath)
        }
        val updated = alarm.copy(targetImagePath = "", targetDescription = "")
        AlarmPreferences.saveAlarm(this, updated)
        Toast.makeText(this, "تم حذف الهدف - المنبه يوقف بضغطة واحدة", Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
