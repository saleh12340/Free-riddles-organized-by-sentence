# ProGuard and R8 rules for optimizing APK size
-keepclassmembers class * {
    @androidx.room.Entity *;
    @androidx.room.Dao *;
}
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
