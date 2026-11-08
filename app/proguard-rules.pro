# Add project specific ProGuard rules here.
-keepattributes *Annotation*
-keepclassmembers class * {
    @org.jetbrains.annotations.Nullable *;
    @org.jetbrains.annotations.NotNull *;
}
# Keep Libretro JNI methods
-keepclasseswithmembernames class com.retro.tvbox.core.LibretroBridge {
    native <methods>;
}
# Ktor & Netty
-dontwarn io.ktor.**
-dontwarn io.netty.**
