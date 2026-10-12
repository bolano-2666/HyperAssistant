# Keep OkHttp & Gson & Coroutines
-keepattributes *Annotation*
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.google.gson.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
