plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.tman.goallock"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tman.goallock"
        minSdk = 29          // Android 10+
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    // One fixed key so every new build installs as an UPDATE (your goals + journal are kept).
    signingConfigs {
        create("shared") {
            storeFile = rootProject.file("keystore/goallock.jks")
            storePassword = "goallock"
            keyAlias = "goallock"
            keyPassword = "goallock"
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("shared")
        }
        getByName("debug") {
            signingConfig = signingConfigs.getByName("shared")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

// No extra libraries: the app uses only built-in Android classes, so it stays small and simple.
dependencies { }
