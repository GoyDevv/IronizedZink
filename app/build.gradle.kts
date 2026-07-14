import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    // AGP 9 ships built-in Kotlin support, so the kotlin-android plugin is no longer
    // applied; only the Compose compiler plugin is needed on top.
    alias(libs.plugins.kotlin.compose)
}

// Release signing is driven entirely by environment variables that only exist in the
// Release workflow (the keystore is stored as a GitHub Actions secret, never committed).
val signingKeystoreFile: String? = System.getenv("SIGNING_KEYSTORE_FILE")
val hasReleaseSigning: Boolean = !signingKeystoreFile.isNullOrBlank() && file(signingKeystoreFile).exists()

android {
    namespace = "com.goydevv.ironizedzink"
    compileSdk = 37
    ndkVersion = "25.2.9519653"

    defaultConfig {
        applicationId = "com.goydevv.ironizedzink"
        // Native Zink + Kopper stack is built for Android 26 (NDK r27d).
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "1.0.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        // Only ship the ABIs we actually provide native libraries for.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
        }
    }

    signingConfigs {
        create("release") {
            if (hasReleaseSigning) {
                storeFile = file(signingKeystoreFile!!)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // Keep disabled: shrinking a renderer plugin brings no benefit and
            // risks stripping the meta-data/entry the launcher relies on.
            isMinifyEnabled = false
            // Use the stable release key when available (CI Release), otherwise fall
            // back to the debug key so the APK is at least installable.
            signingConfig = if (hasReleaseSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            // Extract .so to the app's nativeLibraryDir so the launcher can
            // dlopen("$nativeLibraryDir/libEGL_mesa.so") etc.
            useLegacyPackaging = true
            // Our prebuilt Mesa/Zink .so are already stripped; never re-strip them.
            keepDebugSymbols += "**/*.so"
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        optIn.addAll("androidx.compose.material3.ExperimentalMaterial3Api")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation("androidx.compose.animation:animation")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
