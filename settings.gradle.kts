pluginManagement {
    repositories {
        // 国内镜像（阿里云）：官方源慢或超时时的加速方案
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/public")
        // 官方源保留在后，镜像缺包时可回退
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // 国内镜像（阿里云）
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        // 官方源保留在后，镜像缺包时可回退
        google()
        mavenCentral()
    }
}

rootProject.name = "YETVPlusNative"
include(":app")
