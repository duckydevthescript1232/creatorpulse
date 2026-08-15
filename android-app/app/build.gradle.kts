plugins {
    id("com.android.application")
}

android {
    namespace = "com.creatorpulse.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.creatorpulse.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "1.4.0"
        buildConfigField("String", "WEB_APP_URL", "\"https://creatorpulse.creatorpulseapp.workers.dev\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            // Debug APK keeps the production package ID for direct installation.
        }
    }

    buildFeatures {
        buildConfig = true
    }
}
