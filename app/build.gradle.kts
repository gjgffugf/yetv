plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.yetvplus.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.yetvplus.app"
        minSdk = 23
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        // 本地/云端可选签名：传入 -PMTV_STORE_FILE=xxx.jks 即启用
        create("release") {
            val sf = project.findProperty("MTV_STORE_FILE") as String?
            if (!sf.isNullOrBlank()) {
                storeFile = file(sf)
                storePassword = (project.findProperty("MTV_STORE_PASSWORD") as String?) ?: ""
                keyAlias = (project.findProperty("MTV_KEY_ALIAS") as String?) ?: ""
                keyPassword = (project.findProperty("MTV_KEY_PASSWORD") as String?) ?: ""
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig =
                if (signingConfigs.getByName("release").storeFile != null)
                    signingConfigs.getByName("release")
                else
                    signingConfigs.getByName("debug")
        }
        getByName("debug") {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packagingOptions {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.activity:activity-ktx:1.8.2")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("com.google.android.material:material:1.11.0")
}
