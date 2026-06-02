plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    // KSP – processes Hilt annotations at compile time to generate dependency injection boilerplate
    alias(libs.plugins.ksp)
    // Hilt plugin – enables @HiltAndroidApp and @AndroidEntryPoint component generation in this module
    alias(libs.plugins.hilt.android)
}

android {
    namespace = "com.thiagoneves.bleconnection"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.thiagoneves.bleconnection"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {

    // ──────────────────────────────────────────────────────────────────────
    // CORE ANDROID + COMPOSE
    // ──────────────────────────────────────────────────────────────────────

    // Core KTX – Kotlin extensions for Android framework APIs (Context, SharedPreferences, etc.)
    implementation(libs.androidx.core.ktx)

    // Lifecycle runtime KTX – lets us use lifecycleScope for launching coroutines tied to Activity lifecycle
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Activity Compose – setContent {} entry point that bridges Activity lifecycle with Compose
    implementation(libs.androidx.activity.compose)

    // Compose BOM (Bill of Materials) – locks ALL Compose library versions together so they're compatible.
    // Without this, mixing different Compose versions causes runtime crashes.
    implementation(platform(libs.androidx.compose.bom))

    // Compose UI – the core rendering engine for declarative UI (layouts, drawing, input)
    implementation(libs.androidx.compose.ui)

    // Compose UI Graphics – low-level graphics primitives (Canvas, ImageBitmap) used by the UI toolkit
    implementation(libs.androidx.compose.ui.graphics)

    // Compose UI Tooling Preview – enables @Preview annotations so we can preview composables in the IDE
    implementation(libs.androidx.compose.ui.tooling.preview)

    // Material 3 – Google's latest design system components (Scaffold, TopAppBar, Button, Card, etc.)
    implementation(libs.androidx.compose.material3)

    // ──────────────────────────────────────────────────────────────────────
    // VIEWMODEL + LIFECYCLE (MVVM pattern)
    // ──────────────────────────────────────────────────────────────────────

    // ViewModel Compose – provides viewModel() in composables; ViewModel survives rotation and holds
    // BLE scan results, connection state, and heart-rate readings across configuration changes
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Lifecycle Runtime Compose – provides collectAsStateWithLifecycle() which automatically stops
    // collecting Flows when the UI is in the background, preventing wasted BLE work and battery drain
    implementation(libs.androidx.lifecycle.runtime.compose)

    // ──────────────────────────────────────────────────────────────────────
    // KOTLIN COROUTINES (async BLE operations)
    // ──────────────────────────────────────────────────────────────────────

    // Coroutines Core – provides suspend functions, Flow, StateFlow, and channels.
    // BLE scanning and GATT reads are callback-based; coroutines turn them into clean sequential code.
    implementation(libs.kotlinx.coroutines.core)

    // Coroutines Android – adds Dispatchers.Main so coroutine results can safely update Compose UI
    implementation(libs.kotlinx.coroutines.android)

    // ──────────────────────────────────────────────────────────────────────
    // HILT – Dependency Injection (wiring MVVM layers together)
    // ──────────────────────────────────────────────────────────────────────

    // Hilt Android – runtime DI library. Injects the BLE scanner/repository into ViewModels
    // and ViewModels into composables, so each layer is decoupled and testable.
    implementation(libs.hilt.android)

    // Hilt Compiler (KSP) – generates the Dagger component implementations at compile time.
    // Without this, Hilt annotations like @Inject and @HiltViewModel would do nothing.
    ksp(libs.hilt.android.compiler)

    // Hilt Navigation Compose – provides hiltViewModel() which creates a ViewModel
    // scoped to a NavGraph destination AND injects its dependencies automatically.
    implementation(libs.hilt.navigation.compose)

    // ──────────────────────────────────────────────────────────────────────
    // ACCOMPANIST PERMISSIONS (runtime BLE permission requests)
    // ──────────────────────────────────────────────────────────────────────

    // Accompanist Permissions – Compose-native permission API. Replaces the verbose
    // ActivityResultContracts boilerplate with a simple rememberPermissionState() call.
    // We use it to request BLUETOOTH_SCAN, BLUETOOTH_CONNECT, and ACCESS_FINE_LOCATION at runtime.
    implementation(libs.accompanist.permissions)

    // ──────────────────────────────────────────────────────────────────────
    // TESTING
    // ──────────────────────────────────────────────────────────────────────

    // JUnit 4 – the standard unit test framework for assertions, test lifecycle (@Before, @After)
    testImplementation(libs.junit)

    // Kotlin Coroutines Test – provides runTest{} which uses VIRTUAL TIME (no real delays).
    // Also provides StandardTestDispatcher for controlling coroutine execution order in tests,
    // and advanceUntilIdle() to process all pending coroutines before asserting results.
    testImplementation(libs.kotlinx.coroutines.test)

    // Turbine – Cash App's Flow testing library. Provides a .test{} DSL that collects
    // flow emissions into an internal queue, so you can assert them one-by-one with awaitItem().
    // Much cleaner than toList() + timeout hacks for testing Flows.
    testImplementation(libs.turbine)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}