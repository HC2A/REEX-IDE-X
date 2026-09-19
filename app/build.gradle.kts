plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.reex.idex"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.reex.idex"
        minSdk = 23
        targetSdk = 36
        versionCode = 31
        versionName = "3.2.0-flutter-runtime"
        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86_64")
        }
    }

    androidResources {
        localeFilters += listOf("en", "ar")
    }

    buildFeatures {
        compose = true
    }

    buildTypes {
        debug {
            isDebuggable = true
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            isShrinkResources = false
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

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    packaging {
        resources.excludes += setOf(
            "META-INF/DEPENDENCIES",
            "META-INF/LICENSE",
            "META-INF/LICENSE.txt",
            "META-INF/NOTICE",
            "META-INF/NOTICE.txt"
        )
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.1")

    implementation("io.github.rosemoe:editor:0.24.4")
    implementation("io.github.rosemoe:language-textmate:0.24.4")
    implementation("io.github.rosemoe:oniguruma-native:0.24.4")

    // CI generates the official Flutter module AAR repository before this build.
    // Keeping the dependency conditional preserves source-buildability of the editor
    // while release CI packages the real Flutter Engine runtime.
    val flutterRepo = rootProject.file("app/flutter_repo")
    if (flutterRepo.exists()) {
        debugImplementation("com.reex.runtime.flutter_runtime:flutter_debug:1.0")
        releaseImplementation("com.reex.runtime.flutter_runtime:flutter_release:1.0")
    }
}
