plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.wisp.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.wisp.app"
        // API 29 (Android 10) floor: MediaProjection's foreground-service-type
        // enforcement started here, and coding against one consistent model
        // (rather than branching pre/post-29 capture flows) keeps
        // CaptureManager/OverlayService simple. See docs/memory.md if this
        // ever needs lowering for a specific device request.
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.1")
    implementation("androidx.lifecycle:lifecycle-service:2.8.1")
    implementation("androidx.activity:activity-compose:1.9.0")

    // Compose, used both in MainActivity (permission-request UI, Settings)
    // and inside the floating overlay window itself (a ComposeView hosted
    // directly in a WindowManager-added window -- see overlay/OverlayView.kt).
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // BYOK key storage -- EncryptedSharedPreferences backed by the Android
    // Keystore, a strict improvement over the desktop app's plaintext
    // settings.json (see src/main/settings.ts on the desktop side; easy to
    // do properly here, so we do).
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // HTTP + SSE streaming for the LLM provider clients (providers/*.kt),
    // mirroring backend/providers/*.py's shape on the desktop app.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-sse:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.json:json:20240303")

    testImplementation("junit:junit:4.13.2")
}
