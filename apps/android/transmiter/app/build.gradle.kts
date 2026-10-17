plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
}

// The proprietary native broadcast engine (C++ + AudioStreamer) is git-ignored,
// so it exists only in local builds. Everything native is gated on this flag.
val hasNativeEngine = file("src/main/cpp/CMakeLists.txt").exists()

android {
    namespace = "lk.sugaapps.airwave"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "lk.sugaapps.airwave"
        minSdk = 24
        targetSdk = 37
        versionCode = 6
        versionName = "1.5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Native engine build — only when the private C++ is present (see flag above).
    if (hasNativeEngine) {
        defaultConfig {
            ndk {
                // arm64 covers modern devices; add more ABIs for release as needed.
                abiFilters += setOf("arm64-v8a")
            }
            externalNativeBuild {
                cmake { arguments += "-DANDROID_STL=c++_shared" }
            }
        }
        externalNativeBuild {
            cmake {
                path = file("src/main/cpp/CMakeLists.txt")
                version = "3.22.1"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        prefab = hasNativeEngine // exposes Oboe's headers/libs to CMake
    }
}

dependencies {
    // Native audio engine dependency (Oboe) — only with the private engine present.
    if (hasNativeEngine) {
        implementation("com.google.oboe:oboe:1.9.3")
    }

    // Firebase (Auth + Firestore) + Google Sign-In via Credential Manager
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.functions)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
