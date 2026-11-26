# JS 브리지 메서드는 리플렉션으로 호출됨
-keepclassmembers class app.ourtoday.NativeBridge {
    @android.webkit.JavascriptInterface <methods>;
}

# EncryptedSharedPreferences(Tink) R8 보호
-keep class com.google.crypto.tink.** { *; }
-keep class androidx.security.crypto.** { *; }
-dontwarn com.google.crypto.tink.**
