plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.yetvplus.nativeapp"
    compileSdk = 34

    // 内置签名库：app/yetvplus.jks（有效期 100 年，走外部环境变量时才覆盖）
    val customStoreFile = project.findProperty("MTV_STORE_FILE") as String?
    val defaultStore = file("yetvplus.jks")
    val useCustomSigning = !customStoreFile.isNullOrBlank()
    val useBuiltInSigning = defaultStore.exists()

    defaultConfig {
        applicationId = "com.yetvplus.nativeapp"
        minSdk = 24
        targetSdk = 34
        versionCode = 3
        versionName = "1.0.2"
    }

    signingConfigs {
        create("release") {
            if (useCustomSigning) {
                storeFile = file(customStoreFile!!)
                storePassword = (project.findProperty("MTV_STORE_PASSWORD") as String?) ?: ""
                keyAlias = (project.findProperty("MTV_KEY_ALIAS") as String?) ?: ""
                keyPassword = (project.findProperty("MTV_KEY_PASSWORD") as String?) ?: ""
            } else if (useBuiltInSigning) {
                storeFile = defaultStore
                storePassword = "yetvplus"
                keyAlias = "yetvplus"
                keyPassword = "2287681747yetvplus"
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
            signingConfig = if (useCustomSigning || useBuiltInSigning) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
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
        // material3 的部分 API（如 TextFieldDefaults.outlinedTextFieldColors、Slider）
        // 仍标记为 @ExperimentalMaterial3Api，全局放行，免得逐个文件加注解
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            // media3 的 DefaultMediaSourceFactory 等标了 @UnstableApi，一并放行
            "-opt-in=androidx.media3.common.util.UnstableApi"
        )
    }

    buildFeatures {
        compose = true
        // 应用内更新需要读 BuildConfig.VERSION_CODE / VERSION_NAME
        buildConfig = true
    }

    composeOptions {
        // 与根 build.gradle.kts 的 Kotlin 1.9.10 严格配对，不要单独改
        kotlinCompilerExtensionVersion = "1.5.3"
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2023.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.navigation:navigation-compose:2.7.6")

    // 网络
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")

    // 图片
    implementation("io.coil-kt:coil-compose:2.5.0")

    // 播放器（Media3 / ExoPlayer，支持 HLS）
    implementation("androidx.media3:media3-exoplayer:1.2.0")
    implementation("androidx.media3:media3-exoplayer-hls:1.2.0")
    implementation("androidx.media3:media3-ui:1.2.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
