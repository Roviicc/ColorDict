plugins {
    id("com.android.application")
}

// Address of the dictionary server (the Vercel deployment of api/define.ts).
// Set it with -PdictApiUrl=https://… or the DICT_API_URL environment variable.
// Users can also change it in the app's Settings.
val dictApiUrl: String = (findProperty("dictApiUrl") as String?)
    ?: System.getenv("DICT_API_URL") ?: ""
// Optional shared token; must match APP_TOKEN on the server when that is set.
val dictAppToken: String = (findProperty("dictAppToken") as String?)
    ?: System.getenv("DICT_APP_TOKEN") ?: ""

fun quoted(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "io.github.roviicc.colordict"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.roviicc.colordict"
        minSdk = 24
        targetSdk = 35
        versionCode = 2
        versionName = "2.0.0"
        buildConfigField("String", "API_BASE_URL", quoted(dictApiUrl))
        buildConfigField("String", "APP_TOKEN", quoted(dictAppToken))
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

    lint {
        abortOnError = false
    }
}

dependencies {
    // The app itself is dependency-free: framework APIs only.
    testImplementation("junit:junit:4.13.2")
    // Real org.json for JVM unit tests (Android's copy is stubbed off-device).
    testImplementation("org.json:json:20250517")
}
