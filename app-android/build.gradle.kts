plugins {
    id("com.android.application")
    kotlin("plugin.compose")
    // 搬迁自 JMNeXt 的界面代码里有 @Serializable 模型（如壁纸配置），需要序列化插件
    kotlin("plugin.serialization")
}

android {
    namespace = "com.tricomix.android"
    compileSdk = 37
    compileSdkMinor = 2

    defaultConfig {
        applicationId = "com.tricomix"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        // 搬迁自 JMNeXt 的 LiteFeatures 读这个编译期常量（主项目用 edition 风味定义 full/lite）；测试包取 lite
        buildConfigField("boolean", "LITE", "true")
    }

    buildFeatures {
        compose = true
        // 搬迁自 JMNeXt 的 LiteFeatures 会读 BuildConfig（例如是否启用精简模式）
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildTypes {
        // 测试安装用 debug 签名；Release 签名由用户掌握，不入仓库
        getByName("debug") { isMinifyEnabled = false }
        getByName("release") { isMinifyEnabled = false }
    }
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":core"))
    implementation(project(":source-jm"))
    implementation(project(":source-pica"))
    implementation(project(":source-eh"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    // 搬迁自 JMNeXt 的界面组件需要：Coil 3 加载图片、扩展图标集（版本对齐主项目）
    implementation("io.coil-kt.coil3:coil-compose:3.6.3")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.6.3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
    // Android 模块的单元测试走 JUnit4 运行器（kotlin("test") 在此不会解析到实现）
    testImplementation("junit:junit:4.13.2")
    testImplementation(kotlin("test-junit"))
}
