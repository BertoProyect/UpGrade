plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val buildNumber = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
// Versión visible (1.1.0, 1.1.1…): la calcula GitHub Actions a partir de version.txt.
val appVersion = System.getenv("UPGRADE_VERSION") ?: (rootProject.file("version.txt").readText().trim() + ".0")

android {
    namespace = "com.alberto.upgrade"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.alberto.upgrade"
        minSdk = 26
        targetSdk = 34
        versionCode = buildNumber
        versionName = appVersion
    }

    // La clave de firma vive en los secretos de GitHub (UPGRADE_KEYSTORE_B64 / UPGRADE_KEY_PASSWORD).
    // Sin ella se firma con la clave de depuración de la máquina que compila.
    val ksPath = System.getenv("UPGRADE_KEYSTORE_PATH")
    val ksPass = System.getenv("UPGRADE_KEY_PASSWORD")
    val hasKey = !ksPath.isNullOrBlank() && !ksPass.isNullOrBlank() && file(ksPath).exists()

    signingConfigs {
        if (hasKey) {
            create("upgrade") {
                storeFile = file(ksPath!!)
                storePassword = ksPass
                keyAlias = "upgrade"
                keyPassword = ksPass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (hasKey) signingConfigs.getByName("upgrade") else signingConfigs.getByName("debug")
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
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
