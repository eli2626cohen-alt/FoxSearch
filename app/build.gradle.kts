import java.util.Properties

plugins {
    alias(libs.plugins.android)
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}

val ksFile = localProps.getProperty("KEYSTORE_FILE") ?: System.getenv("KEYSTORE_FILE")
val ksPass = localProps.getProperty("KEYSTORE_PASSWORD") ?: System.getenv("KEYSTORE_PASSWORD")
val kAlias = localProps.getProperty("KEY_ALIAS") ?: System.getenv("KEY_ALIAS")
val kPass = localProps.getProperty("KEY_PASSWORD") ?: System.getenv("KEY_PASSWORD")

android {
    namespace = "com.lockfox.search"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.lockfox.search"
        minSdk = 26
        targetSdk = 34
        versionCode = 13
        versionName = "1.2.1"
    }

    flavorDimensions += "mode"
    productFlavors {
        create("full") {
            dimension = "mode"
            versionNameSuffix = "-full"
        }
        create("safe") {
            dimension = "mode"
            versionNameSuffix = "-safe"
        }
    }

    signingConfigs {
        create("release") {
            if (!ksFile.isNullOrBlank() && file(ksFile).exists()) {
                storeFile = file(ksFile)
                storePassword = ksPass
                keyAlias = kAlias
                keyPassword = kPass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
