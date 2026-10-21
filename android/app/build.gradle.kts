plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.bitcall"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.bitcall"
        minSdk = 30 // Android 11+: phones from 2020 on; microphone foreground service type
        targetSdk = 35
        versionCode = (System.getenv("BITCALL_VERSION_CODE") ?: "1").toInt()
        versionName = System.getenv("BITCALL_VERSION_NAME") ?: "0.1.0-pre"
    }

    // Release signing comes from CI secrets (see .github/workflows/android-prerelease.yml).
    // Without them the release build falls back to the debug key, which is fine for local testing.
    val keystore = System.getenv("BITCALL_KEYSTORE_PATH")
    signingConfigs {
        if (keystore != null) create("release") {
            storeFile = file(keystore)
            storePassword = System.getenv("BITCALL_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("BITCALL_KEY_ALIAS")
            keyPassword = System.getenv("BITCALL_KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    testImplementation("junit:junit:4.13.2")
}
