plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.mosul.salah"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.mosul.salah"
        minSdk = 26
        targetSdk = 33
        // رقم البناء = رقم بناء GitHub حتى زر التحديثات يعرف النسخة الأحدث
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 6
        versionName = "1.7"
    }

    signingConfigs {
        create("release") {
            // المفتاح الرسمي — محفوظ بـ GitHub Secrets (SIGNING_KEY + SIGNING_PASSWORD) ومو موجود بالكود
            storeFile = file(System.getenv("SIGNING_KEY_FILE") ?: "/nonexistent")
            storeType = "pkcs12"
            storePassword = System.getenv("SIGNING_PASSWORD")
            keyAlias = "p2p37mx"
            keyPassword = System.getenv("SIGNING_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}
