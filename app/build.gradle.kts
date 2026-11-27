plugins {
    id("com.android.application")
}

// Upload key for Google Play comes from the environment (GitHub Actions secrets).
// Without it the release build is signed with the debug key: fine for testing, not for Play.
// Unset GitHub secrets arrive as empty strings, treat them as missing
fun env(name: String): String? = System.getenv(name)?.takeIf { it.isNotBlank() }

val uploadKeystore: String? = env("UPLOAD_KEYSTORE")

android {
    namespace = "io.github.eddiechen1234.pocketinterpreter"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.eddiechen1234.pocketinterpreter"
        minSdk = 24
        targetSdk = 36
        // Every CI run gets a higher versionCode, so any build can be uploaded to Play.
        versionCode = env("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionName = "1.0.0"
    }

    signingConfigs {
        if (uploadKeystore != null) {
            create("upload") {
                storeFile = file(uploadKeystore)
                storePassword = env("KEYSTORE_PASS")
                keyAlias = env("KEY_ALIAS") ?: "upload"
                keyPassword = env("KEY_PASS") ?: env("KEYSTORE_PASS")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (uploadKeystore != null) signingConfigs.getByName("upload")
                            else signingConfigs.getByName("debug")
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
    // On-device translation (models download once per language, then work offline)
    implementation("com.google.mlkit:translate:17.0.3")
}
