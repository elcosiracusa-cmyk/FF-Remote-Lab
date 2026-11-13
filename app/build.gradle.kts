plugins {
    id("com.android.application")
}
android {
    namespace = "it.fflab.remote"
    compileSdk = 35
    defaultConfig {
        applicationId = "it.fflab.remote"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "4.0.0"
    }
    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
