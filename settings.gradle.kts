// 插件解析仓库必须显式声明：只留默认的 Gradle 插件门户时，离线环境里解析不到
// 缓存中的 Kotlin 插件标记（缓存里那份来自 mavenCentral），构建会直接失败。
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "TriComiX"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

include(":core")
include(":source-jm")
