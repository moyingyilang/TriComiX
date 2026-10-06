plugins {
    kotlin("jvm")
    // 端点响应是 JSON（包裹在 data.* 里），与 source-jm 用同一套序列化方案
    kotlin("plugin.serialization")
}

kotlin { jvmToolchain(21) }

dependencies {
    implementation(project(":core"))
    // 与主项目 gradle/libs.versions.toml 对齐
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    api("com.squareup.okhttp3:okhttp:5.5.0")
    testImplementation(kotlin("test"))
}
