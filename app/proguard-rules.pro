-keepattributes *Annotation*, JavascriptInterface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.yetvplus.app.** { *; }
-dontwarn android.webkit.**
