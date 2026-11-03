# kotlinx.serialization: keep the generated serializers of the @Serializable models in :core.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class com.giovanni.aeye.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.giovanni.aeye.**$$serializer { *; }

# WorkManager (pulled in by Glance) creates its Room database reflectively via the generated
# *_Impl class; R8 full mode strips it without these.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep class androidx.work.impl.WorkDatabase_Impl { *; }
