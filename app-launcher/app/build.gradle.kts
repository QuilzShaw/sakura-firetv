import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

android {
    namespace = "com.sakura.home"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.sakura.home"
        minSdk = 28
        targetSdk = 30 // Fire OS 8 is Android 11
        versionCode = 6
        versionName = "1.5"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        // Sideloaded onto a Fire TV, never published to Google Play
        disable += "ExpiredTargetSdkVersion"
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

dependencies {
    implementation("androidx.recyclerview:recyclerview:1.4.0")
}
