// 顶层构建：插件版本集中声明（与主项目 JMNeXt 对齐，便于复用已缓存的依赖与插件）
plugins {
    kotlin("jvm") version "2.4.20" apply false
    kotlin("plugin.serialization") version "2.4.20" apply false
    kotlin("plugin.compose") version "2.4.20" apply false
    id("com.android.application") version "9.4.1" apply false
}
