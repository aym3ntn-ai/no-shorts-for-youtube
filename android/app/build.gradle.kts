plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.noshorts.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.noshorts.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    // CI provides these as a base64-encoded PKCS12 keystore (see
    // .github/workflows/android-release.yml). Local debug/lint builds skip
    // signing entirely when they're unset.
    val releaseKeystoreBase64 = System.getenv("ANDROID_KEYSTORE_BASE64")
    signingConfigs {
        if (releaseKeystoreBase64 != null) {
            create("release") {
                val keystoreFile = File.createTempFile("release-signing", ".p12")
                keystoreFile.writeBytes(java.util.Base64.getDecoder().decode(releaseKeystoreBase64))
                keystoreFile.deleteOnExit()

                storeFile = keystoreFile
                storeType = "PKCS12"
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (releaseKeystoreBase64 != null) {
                signingConfig = signingConfigs.getByName("release")
            }
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
        viewBinding = true
    }
}

dependencies {
    implementation(project(":detector"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
}
