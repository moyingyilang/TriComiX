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

// 测试用假凭据：仓库不含真实密钥（开源脱敏），而默认值缺失时会明确报错。
// 这些测试只验证"请求头/签名构造"，不关心密钥真实值。
tasks.withType<Test>().configureEach {
    environment("TRICOMIX_PICA_APIKEY", "TEST-API-KEY-NOT-REAL")
    environment("TRICOMIX_PICA_SIGNINGKEY", "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcde")
}
