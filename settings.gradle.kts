// TriComiX：多源漫画客户端（core + 可插拔源 + Android 应用）
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "TriComiX"

include(":core")
include(":source-jm")
include(":source-pica")
include(":source-eh")
include(":probe")
include(":app-android")
