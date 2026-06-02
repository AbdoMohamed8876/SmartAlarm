# Smart Alarm ProGuard Rules

# Keep model classes
-keep class com.smartalarm.data.model.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# CameraX
-keep class androidx.camera.** { *; }

# Keep receivers and services
-keep class com.smartalarm.receiver.** { *; }
-keep class com.smartalarm.service.** { *; }
