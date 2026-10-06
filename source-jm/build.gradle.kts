plugins {
    kotlin("jvm")
    // 必须启用：统一模型与 DTO 用 @Serializable，没有它会报 "Unresolved reference 'serializer'"
    kotlin("plugin.serialization")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":core"))
    // 版本与主项目 gradle/libs.versions.toml 对齐
    api("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    api("com.squareup.okhttp3:okhttp:5.5.0")
    api("com.squareup.okhttp3:logging-interceptor:5.5.0")
    api("com.squareup.retrofit2:retrofit:3.0.0")
    api("com.squareup.retrofit2:converter-kotlinx-serialization:3.0.0")
    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
}
