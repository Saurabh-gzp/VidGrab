import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val requestedAbis = providers.gradleProperty("targetAbis").orElse("arm64-v8a").get().split(",")
require(requestedAbis.all { it in setOf("arm64-v8a", "armeabi-v7a", "x86_64") })

val keystoreProps = Properties()
val keystoreFile = rootProject.file("keystore.properties")
if (keystoreFile.exists()) {
    keystoreProps.load(keystoreFile.inputStream())
}

android {
    namespace = "com.vidgrab.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.vidgrab.app"
        minSdk = 29
        targetSdk = 36
        versionCode = 31
        versionName = "3.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            // 32-bit x86 drop (dead platform) — splits in sirf ye 3 packaging honge
            abiFilters += requestedAbis
        }
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file(keystoreProps.getProperty("storeFile", "vidgrab.keystore"))
            storePassword = keystoreProps.getProperty("storePassword", "")
            keyAlias = keystoreProps.getProperty("keyAlias", "vidgrab")
            keyPassword = keystoreProps.getProperty("keyPassword", "")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (keystoreFile.exists()) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
        debug {
            signingConfig = if (keystoreFile.exists()) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }

    // One architecture per APK. The default ARM64 build avoids bundling emulator/32-bit runtimes.
    splits { abi { isEnable = false } }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += setOf(
                "META-INF/AL2.0", "META-INF/LGPL2.1",
                "META-INF/DEPENDENCIES", "META-INF/INDEX.LIST",
                "META-INF/*.kotlin_module"
            )
        }
        jniLibs {
            useLegacyPackaging = true
            // 32-bit x86 practically dead (Android 10+ x86 sab 64-bit capable) — 46MB bachao
            excludes += setOf("lib/x86/**", "lib/x86/**/*.so")
        }
    }

    lint {
        // Release build par lintVital sandbox ke 4GB RAM mein OOM karta tha — CI-safe skip
        checkReleaseBuilds = false
        abortOnError = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")
    implementation("androidx.activity:activity-compose:1.10.1")

    implementation(platform("androidx.compose:compose-bom:2025.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.foundation:foundation")
    // NOTE: material-icons-extended intentionally NAHI (11MB dex) — VidIcons custom use karo

    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("io.coil-kt:coil-compose:2.7.0")

    // On-device extraction engine + media merger
    implementation("io.github.junkfood02.youtubedl-android:library:0.18.1")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
