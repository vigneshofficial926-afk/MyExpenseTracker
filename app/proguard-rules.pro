# Add project specific ProGuard rules here.
-keepattributes *Annotation*
-keepclassmembers class * {
    @androidx.room.Entity *;
    @androidx.room.Dao *;
}
