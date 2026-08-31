# The rules from AOSP are located in proguard.flags file, we can just maintain Lawnchair related rules here.

# Optimization options.
-allowaccessmodification
-dontusemixedcaseclassnames
-allowaccessmodification
-keepattributes InnerClasses, *Annotation*, Signature, SourceFile, LineNumberTable

# Remove some Kotlin overhead
-processkotlinnullchecks remove

# Common rules.
-keep class android.window.** { *; }
-keep class android.view.** { *; }

-keepclassmembers class * implements android.os.Parcelable {
  public static final ** CREATOR;
}

# Lawnchair specific rules.
-keep,allowshrinking,allowoptimization class app.lawnchair.LawnchairProto$* { *; }
-keep,allowshrinking,allowoptimization class app.lawnchair.LawnchairApp { *; }
-keep,allowshrinking,allowoptimization class app.lawnchair.LawnchairLauncher { *; }
-keep,allowshrinking,allowoptimization class app.lawnchair.compatlib.** { *; }

# Vellum: surfaces are persisted as JSON via kotlinx.serialization. The DataStore
# string is the only on-disk representation, so keep the model and its serializer
# across R8 full-mode / minifyEnabled release builds. Without this a release build
# can still launch but silently drops the user's surfaces on restart (empty set).
-keep,allowshrinking,allowoptimization class app.lawnchair.vellum.surface.VellumSurface { *; }
-keep,allowshrinking,allowoptimization class app.lawnchair.vellum.surface.VellumSurfaceSet { *; }
-keep,allowshrinking,allowoptimization class app.lawnchair.util.ComponentKeySerializer { *; }
-keep,allowshrinking,allowoptimization class app.lawnchair.util.IntentSerializer { *; }
-keepclassmembers class app.lawnchair.vellum.surface.VellumSurface { *; }
-keepclassmembers class app.lawnchair.vellum.surface.VellumSurfaceSet { *; }
# kotlinx.serialization: keep generated serializers and the Json entries they reference.
-keepclassmembers class kotlinx.serialization.json.** { *; }
-keep @kotlinx.serialization.Serializable class * { *; }

-keep,allowshrinking,allowoptimization class com.google.protobuf.Timestamp { *; }
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }

# We intentionally remove it to replace Smartspacer's widget popup with our own Launcher3 popup
-dontwarn com.skydoves.balloon.*

# This shouldn't concern us much
-dontwarn androidx.window.extensions.**
-dontwarn androidx.window.sidecar.**
