plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "io.github.roviicc.colordict"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.roviicc.colordict"
        minSdk = 24
        targetSdk = 35
        versionCode = 4
        versionName = "1.3.0"
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

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        abortOnError = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.10.0")
    // FileProvider, for handing an exported report log to the share sheet.
    implementation("androidx.core:core:1.15.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Storybook-style, searchable component browser for native Compose UI.
    implementation("com.airbnb.android:showkase-annotation:1.0.5")
    debugImplementation("com.airbnb.android:showkase:1.0.5")
    kspDebug("com.airbnb.android:showkase-processor:1.0.5")

    testImplementation("junit:junit:4.13.2")
}

ksp {
    arg("skipPrivatePreviews", "true")
}
