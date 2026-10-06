plugins {
    kotlin("jvm")
}

kotlin { jvmToolchain(21) }

dependencies {
    implementation(project(":core"))
    // 签名层只用 JDK 自带的 javax.crypto（HMAC-SHA256），不需要额外依赖
    testImplementation(kotlin("test"))
}
