// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    // KSP – Kotlin Symbol Processing; Hilt uses it instead of KAPT for faster annotation processing
    alias(libs.plugins.ksp) apply false
    // Hilt Gradle plugin – registers Hilt's component generation with the Android build pipeline
    alias(libs.plugins.hilt.android) apply false
}