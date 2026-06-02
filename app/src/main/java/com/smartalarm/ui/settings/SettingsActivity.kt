package com.smartalarm.ui.settings

import android.os.Bundle
import android.text.InputType
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.smartalarm.ai.GeminiVerifier
import com.smartalarm.data.prefs.AlarmPreferences
import com.smartalarm.databinding.ActivitySettingsBinding
import kotlinx.coroutines.launch

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.apply {
            title = "الإعدادات"
            setDisplayHomeAsUpEnabled(true)
        }

        loadSettings()
        setupButtons()
    }

    private fun loadSettings() {
        val apiKey = AlarmPreferences.getGeminiApiKey(this)
        binding.etApiKey.setText(apiKey)
        binding.tvApiStatus.text = if (apiKey.isNotEmpty()) "✅ تم حفظ مفتاح API" else "❌ لم يتم إدخال مفتاح API"
    }

    private fun setupButtons() {
        binding.btnSaveApiKey.setOnClickListener {
            val key = binding.etApiKey.text.toString().trim()
            if (key.isEmpty()) {
                Toast.makeText(this, "أدخل مفتاح API أولاً", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            binding.btnSaveApiKey.isEnabled = false
            binding.btnSaveApiKey.text = "جاري التحقق..."

            lifecycleScope.launch {
                val verifier = GeminiVerifier(key)
                val valid = verifier.validateApiKey()
                runOnUiThread {
                    binding.btnSaveApiKey.isEnabled = true
                    binding.btnSaveApiKey.text = "حفظ المفتاح"
                    if (valid) {
                        AlarmPreferences.saveGeminiApiKey(this@SettingsActivity, key)
                        binding.tvApiStatus.text = "✅ مفتاح API صحيح وتم حفظه"
                        Toast.makeText(this@SettingsActivity, "✅ تم حفظ المفتاح بنجاح!", Toast.LENGTH_SHORT).show()
                    } else {
                        binding.tvApiStatus.text = "❌ المفتاح غير صحيح"
                        Toast.makeText(this@SettingsActivity, "المفتاح غير صحيح أو لا يوجد اتصال إنترنت", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        binding.btnHowToGetKey.setOnClickListener { showApiKeyHelp() }
        binding.btnToggleKeyVisibility.setOnClickListener {
            val et = binding.etApiKey
            val isHidden = et.inputType == (InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
            if (isHidden) {
                et.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                binding.btnToggleKeyVisibility.text = "🙈"
            } else {
                et.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                binding.btnToggleKeyVisibility.text = "👁"
            }
            et.setSelection(et.text.length)
        }
    }

    private fun showApiKeyHelp() {
        AlertDialog.Builder(this)
            .setTitle("كيف تحصل على مفتاح Gemini API مجاناً؟")
            .setMessage(
                "1️⃣ افتح المتصفح وادخل على:\n   aistudio.google.com\n\n" +
                "2️⃣ سجّل دخول بحساب Google\n\n" +
                "3️⃣ اضغط على 'Get API Key'\n\n" +
                "4️⃣ اضغط 'Create API key'\n\n" +
                "5️⃣ انسخ المفتاح والصقه هنا\n\n" +
                "✅ المفتاح مجاني تماماً\n" +
                "✅ يعطيك 1500 طلب يومياً مجاناً"
            )
            .setPositiveButton("فهمت", null)
            .show()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
