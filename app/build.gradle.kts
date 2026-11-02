plugins {
    id("com.android.application")
}

android {
    namespace = "io.github.roviicc.colordict"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.roviicc.colordict"
        // 26+: the Claude Java SDK uses java.time.
        minSdk = 26
        targetSdk = 35
        // Must keep increasing: v1.4.1 shipped with versionCode 6.
        versionCode = 7
        versionName = "2.0.0"
    }

    signingConfigs {
        // A fixed, public debug key (password "android") so every CI build and
        // release is signed the same way and installs as an update over the last.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildFeatures {
        buildConfig = true
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

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        resources {
            excludes += setOf("META-INF/versions/9/OSGI-INF/MANIFEST.MF", "META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*")
        }
    }

    lint {
        abortOnError = false
    }
}

dependencies {
    // Official Claude SDK; the app calls the API directly with the user's own key.
    implementation("com.anthropic:anthropic-java:3.3.0")
    testImplementation("junit:junit:4.13.2")
    // Real org.json for JVM unit tests (Android's copy is stubbed off-device).
    testImplementation("org.json:json:20250517")
    // Local fake of the Claude API for ClaudeDictionaryTest (same OkHttp as the SDK).
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
