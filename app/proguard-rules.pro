-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
-dontwarn javax.annotation.**

-keep class com.jcraft.jsch.** { *; }
-dontwarn com.jcraft.jsch.**
