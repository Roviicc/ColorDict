plugins {
    id("com.android.application")
}

android {
    namespace = "io.github.roviicc.colordict"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.roviicc.colordict"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
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

    lint {
        abortOnError = false
    }
}

dependencies {
    // The app itself is intentionally dependency-free: framework APIs only.
    testImplementation("junit:junit:4.13.2")
}
