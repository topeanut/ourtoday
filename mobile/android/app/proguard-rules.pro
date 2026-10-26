# JS 브리지 메서드는 리플렉션으로 호출됨
-keepclassmembers class app.ourtoday.NativeBridge {
    @android.webkit.JavascriptInterface <methods>;
}
