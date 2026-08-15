import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Release signing. The keystore and its password live OUTSIDE this repo and are never committed.
// Point BG_KEYSTORE_PROPS at a properties file holding storeFile / storePassword / keyAlias /
// keyPassword. Without it the build simply produces an unsigned release — which is the right
// outcome on any machine that is not the release machine.
//
// ⚠ Losing that keystore means this app can never be updated again for anyone who installed it.
// Android identifies an app by its signature: a differently-signed APK with the same package name
// is a *different app* to the OS, and every existing install would have to be removed by hand.
// Back it up like a permanent credential, because that is exactly what it is.
val keystoreProps = Properties().apply {
    val f = file(
        System.getenv("BG_KEYSTORE_PROPS")
            ?: "${System.getProperty("user.home")}/.breathgate/keystore.properties"
    )
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    // applicationId ruled 2026-08-13: BreathGate. `dev.` prefix because breathgate.app and .com are
    // taken while breathgate.dev is free. It must NOT correlate to the anon domain (Website/Public-Face).
    namespace = "dev.breathgate"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.breathgate"
        minSdk = 26
        targetSdk = 35
        versionCode = 15
        versionName = "0.12.1"
    }

    // Two flavours, one codebase (ruled 2026-08-13):
    //   gift — Obtainium / F-Droid / direct APK; donate freely, no billing
    //   play — Play Store; cosmetics via Play Billing, donate button per the policy audit
    flavorDimensions += "distribution"
    productFlavors {
        create("gift") { dimension = "distribution" }
        create("play") { dimension = "distribution" }
    }

    signingConfigs {
        create("release") {
            if (keystoreProps.getProperty("storeFile") != null) {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystoreProps.getProperty("storeFile") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    // BuildConfig.VERSION_NAME — the support screen states its own version so a feature request
    // arrives already saying which build it came from. Off by default in AGP 8.
    buildFeatures { buildConfig = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
}
