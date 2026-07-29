pluginManagement {
    val flutterSdkPath =
        run {
            val properties = java.util.Properties()
            file("local.properties").inputStream().use { properties.load(it) }
            val flutterSdkPath = properties.getProperty("flutter.sdk")
            require(flutterSdkPath != null) { "flutter.sdk not set in local.properties" }
            flutterSdkPath
        }

    includeBuild("$flutterSdkPath/packages/flutter_tools/gradle")

    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("dev.flutter.flutter-plugin-loader") version "1.0.0"
    // AGP 8, not 9. Two plugins in this dependency set cannot both build under AGP 9: `file_picker`
    // 11.0.2 skips applying KGP when it detects AGP 9 (expecting built-in Kotlin) without checking
    // whether `android.builtInKotlin` is actually on, so its five Kotlin sources — including
    // `FilePickerPlugin` — never compile and `GeneratedPluginRegistrant.java` fails to resolve the
    // class. Turning built-in Kotlin on instead breaks `flutter_plugin_android_lifecycle` 2.0.35
    // (transitive via `image_picker`), which AGP 9 then rejects for having KGP applied around it.
    // Revisit when both plugins ship AGP 9 support; the app module itself is agnostic.
    id("com.android.application") version "8.13.1" apply false
    id("org.jetbrains.kotlin.android") version "2.3.20" apply false
}

include(":app")
