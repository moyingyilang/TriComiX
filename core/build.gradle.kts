plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    // 只用标准库：ComicSource 的 suspend 来自语言本身，暂不引入 kotlinx-coroutines，
    // 这样 core 可以在完全离线的环境里构建与测试。
    testImplementation(kotlin("test"))
}
