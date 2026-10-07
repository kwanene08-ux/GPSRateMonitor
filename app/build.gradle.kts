import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val signingProperties = Properties()
val signingFile = file(
    System.getProperty("user.home") +
        "/.gradle/gpsratemonitor/signing.properties"
)

if (signingFile.exists()) {
    signingFile.inputStream().use {
        signingProperties.load(it)
    }
}
android {
    namespace = "com.kwan.gpsratemonitor"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.kwan.gpsratemonitor"
        minSdk = 26
        targetSdk = 35
        versionCode = 12
        versionName = "1.2.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }

    signingConfigs {
        create("release") {
            storeFile = file(
                signingProperties["storeFile"].toString()
            )
            storePassword =
                signingProperties["storePassword"].toString()
            keyAlias =
                signingProperties["keyAlias"].toString()
            keyPassword =
                signingProperties["keyPassword"].toString()
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("release")
        }
    }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.01.00"))
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
