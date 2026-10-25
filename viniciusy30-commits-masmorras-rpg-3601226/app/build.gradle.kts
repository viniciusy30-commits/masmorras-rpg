plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.viniciusy.masmorras"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.viniciusy.masmorras"
        minSdk = 24
        targetSdk = 34
        val runNumber = System.getenv("GITHUB_RUN_NUMBER") ?: "1"
        versionCode = runNumber.toIntOrNull() ?: 1
        versionName = "1.$runNumber"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("masmorras.keystore")
            storePassword = "masmorras123"
            keyAlias = "masmorras"
            keyPassword = "masmorras123"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            signingConfig = signingConfigs.getByName("debug")
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
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
}
