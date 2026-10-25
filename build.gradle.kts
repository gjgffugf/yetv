plugins {
    id("com.android.application") version "8.1.4" apply false
    // 必须与 app/build.gradle.kts 里的 composeOptions.kotlinCompilerExtensionVersion 严格配对：
    // Compose Compiler 1.5.3  ⇔  Kotlin 1.9.10
    // （1.9.22 需要 Compose Compiler 1.5.7+，改一个就必须改另一个）
    id("org.jetbrains.kotlin.android") version "1.9.10" apply false
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
