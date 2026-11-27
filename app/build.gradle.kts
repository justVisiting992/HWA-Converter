plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.github.justvisiting992.mediaconverter"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.justvisiting992.mediaconverter"
        minSdk = 24
        targetSdk = 35
        versionCode = 5
        versionName = "0.2.3"
    }

    // One APK per ABI the FFmpegKit engine actually ships, plus a
    // universal APK carrying both: app-arm64-v8a, app-x86_64,
    // app-universal. (armeabi-v7a does not exist in any maintained
    // FFmpegKit build — the fork ships arm64-v8a + x86_64 only.)
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "x86_64")
            isUniversalApk = true
        }
    }

    // Release signing. The keystore is never in git: on a tagged build
    // the workflow decodes it from the KEYSTORE_BASE64 secret into
    // app/release.keystore and passes the passwords as environment
    // variables. Without that file — every local build — the release
    // build type stays unsigned instead of failing, and debug builds
    // are never affected.
    signingConfigs {
        create("release") {
            storeFile = file("release.keystore")
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            if (file("release.keystore").exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        // For BuildConfig.VERSION_NAME on the About screen.
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // FFmpegKit (maintained fork) is vendored as an AAR in app/libs — see
    // VENDORED.md. It is deliberately NOT a Maven dependency: when the
    // original FFmpegKit's binaries vanished from Maven Central in 2025,
    // every app that depended on them stopped building.
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))
    // FFmpegKit's only transitive dependency.
    implementation("com.arthenica:smart-exception-java:0.2.1")
}
