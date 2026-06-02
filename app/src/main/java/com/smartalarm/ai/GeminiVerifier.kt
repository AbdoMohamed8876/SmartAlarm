package com.smartalarm.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class GeminiVerifier(private val apiKey: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    data class VerificationResult(
        val isMatch: Boolean,
        val confidence: Float,
        val reason: String,
        val detectedObjects: List<String> = emptyList()
    )

    // First call: describe what the target image contains
    suspend fun describeTarget(targetBase64: String): String = withContext(Dispatchers.IO) {
        val prompt = """
            أنت نظام للتعرف على الأشياء. انظر إلى هذه الصورة وصف بدقة:
            1. الأشياء الرئيسية الموجودة في الصورة
            2. الألوان والشكل العام
            3. موقع الأشياء في الصورة
            أجب باللغة العربية في سطر واحد موجز لا يتجاوز 30 كلمة.
        """.trimIndent()

        val result = callGeminiApi(prompt, targetBase64, null)
        result.getOrDefault("وصف غير متاح")
    }

    // Main call: compare live photo with target
    suspend fun verifyMatch(
        targetBase64: String,
        liveBase64: String,
        targetDescription: String
    ): VerificationResult = withContext(Dispatchers.IO) {

        val prompt = """
            أنت نظام للتحقق من هوية الأماكن والأشياء. 
            
            الصورة الأولى (المرجع): تحتوي على: $targetDescription
            الصورة الثانية (الحالية): التقطها المستخدم الآن.
            
            مهمتك: هل الصورة الحالية تُظهر نفس المكان أو الشيء الموجود في صورة المرجع؟
            
            قواعد التقييم:
            - لا يشترط أن تكون الزاوية متطابقة تماماً
            - يكفي أن يكون نفس الشيء الأساسي مرئياً (مثلاً: نفس التلفزيون، نفس الباب، نفس المكتب)
            - تجاهل اختلافات الإضاءة البسيطة
            - كن مرناً في التقييم إذا كان الشيء الرئيسي واضحاً
            
            أجب بتنسيق JSON فقط (لا تضف أي نص آخر):
            {
              "match": true/false,
              "confidence": 0.0-1.0,
              "reason": "سبب موجز",
              "detected": ["شيء1", "شيء2"]
            }
        """.trimIndent()

        return@withContext try {
            val responseText = callGeminiApi(prompt, targetBase64, liveBase64).getOrThrow()
            parseVerificationResponse(responseText)
        } catch (e: Exception) {
            VerificationResult(false, 0f, "خطأ في التحقق: ${e.message}")
        }
    }

    // Quick check without full comparison (faster)
    suspend fun quickVerify(
        liveBase64: String,
        targetDescription: String
    ): VerificationResult = withContext(Dispatchers.IO) {

        val prompt = """
            انظر إلى هذه الصورة. هل تحتوي على: $targetDescription
            
            أجب بـ JSON فقط:
            {
              "match": true/false,
              "confidence": 0.0-1.0,
              "reason": "سبب موجز",
              "detected": ["شيء1"]
            }
        """.trimIndent()

        return@withContext try {
            val responseText = callGeminiApi(prompt, null, liveBase64).getOrThrow()
            parseVerificationResponse(responseText)
        } catch (e: Exception) {
            VerificationResult(false, 0f, "خطأ: ${e.message}")
        }
    }

    private fun callGeminiApi(
        prompt: String,
        image1Base64: String?,
        image2Base64: String?
    ): Result<String> {
        return try {
            val parts = JSONArray()

            // Add images if provided
            image1Base64?.let {
                parts.put(JSONObject().apply {
                    put("inline_data", JSONObject().apply {
                        put("mime_type", "image/jpeg")
                        put("data", it)
                    })
                })
            }
            image2Base64?.let {
                parts.put(JSONObject().apply {
                    put("inline_data", JSONObject().apply {
                        put("mime_type", "image/jpeg")
                        put("data", it)
                    })
                })
            }

            // Add text prompt
            parts.put(JSONObject().apply {
                put("text", prompt)
            })

            val body = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", parts)
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.1)
                    put("maxOutputTokens", 500)
                })
            }

            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: return Result.failure(Exception("Empty response"))

            if (!response.isSuccessful) {
                return Result.failure(Exception("API Error ${response.code}: $responseBody"))
            }

            val json = JSONObject(responseBody)
            val text = json
                .getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")

            Result.success(text.trim())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseVerificationResponse(text: String): VerificationResult {
        return try {
            // Extract JSON from response (remove any markdown backticks)
            val cleaned = text
                .replace("```json", "")
                .replace("```", "")
                .trim()

            val json = JSONObject(cleaned)
            val detected = mutableListOf<String>()
            val detectedArr = json.optJSONArray("detected")
            detectedArr?.let {
                for (i in 0 until it.length()) detected.add(it.getString(i))
            }

            VerificationResult(
                isMatch = json.optBoolean("match", false),
                confidence = json.optDouble("confidence", 0.0).toFloat(),
                reason = json.optString("reason", ""),
                detectedObjects = detected
            )
        } catch (e: Exception) {
            // Fallback: look for keywords
            val lower = text.lowercase()
            val isMatch = lower.contains("true") || lower.contains("نعم") || lower.contains("يطابق")
            VerificationResult(isMatch, if (isMatch) 0.7f else 0.3f, text.take(100))
        }
    }

    suspend fun validateApiKey(): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val body = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", "قل 'مرحبا' فقط") })
                        })
                    })
                })
            }
            val request = Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }
}
