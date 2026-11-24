import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// Release builds get their version from the tag, for example APP_VERSION=v0.3.0.
// versionCode is major * 10000 + minor * 100 + patch, so each release installs over the last.
val appVersion = System.getenv("APP_VERSION").orEmpty().removePrefix("v").ifEmpty { "0.0.0-dev" }
val appVersionCode = appVersion.substringBefore("-").split(".").let { parts ->
    val n = parts.map { it.toIntOrNull() ?: 0 } + listOf(0, 0, 0)
    maxOf(1, n[0] * 10000 + n[1] * 100 + n[2])
}

android {
    namespace = "app.tripplanner.fieldguide"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.tripplanner.fieldguide"
        minSdk = 26
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersion
    }

    androidResources {
        localeFilters += "en"
    }

    val keystorePath = System.getenv("RELEASE_KEYSTORE_FILE").orEmpty()
    val releaseSigning = if (keystorePath.isNotEmpty()) {
        signingConfigs.create("release") {
            storeFile = file(keystorePath)
            storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("RELEASE_KEY_ALIAS")
            keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
        }
    } else {
        null
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            // A dev server on the LAN or emulator host is plain http.
            manifestPlaceholders["cleartext"] = "true"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            manifestPlaceholders["cleartext"] = "false"
            // Without a release keystore in the environment the APK is signed with the debug key so it still installs.
            signingConfig = releaseSigning ?: signingConfigs.getByName("debug")
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
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/*.version", "DebugProbesKt.bin", "kotlin-tooling-metadata.json")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    // Vector icons only; R8 strips every icon that is not referenced.
    implementation(libs.compose.icons.extended)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.work.runtime)
    implementation(libs.coroutines.android)
    implementation(libs.serialization.json)
    implementation(libs.okhttp)

    testImplementation(libs.junit)
}
