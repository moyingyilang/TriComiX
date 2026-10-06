plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

kotlin { jvmToolchain(21) }

dependencies {
    implementation(project(":core"))
    // 与主项目 gradle/libs.versions.toml 对齐；jsoup 用于解析 HTML，序列化用于分页 JSON
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    api("com.squareup.okhttp3:okhttp:5.5.0")
    api("org.jsoup:jsoup:1.18.3")
    testImplementation(kotlin("test"))
}
