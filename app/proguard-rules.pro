# Add project specific ProGuard rules here.
-keep class com.openclaw.clawview.** { *; }
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
