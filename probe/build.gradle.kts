plugins {
    kotlin("jvm")
    application
}

kotlin { jvmToolchain(21) }

dependencies {
    implementation(project(":core"))
    implementation(project(":source-eh"))
    implementation(project(":source-pica"))
    implementation(project(":source-jm"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
}

application {
    mainClass.set("com.tricomix.probe.MainKt")
    applicationDefaultJvmArgs = listOf("-Dfile.encoding=UTF-8", "-Djava.net.preferIPv4Stack=true")
}

/** 开发期原始响应转储：gradle :probe:rawDump -PrawArgs="<comicId>" */
tasks.register<JavaExec>("rawDump") {
    group = "verification"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.tricomix.probe.RawKt")
    jvmArgs("-Djava.net.preferIPv4Stack=true", "-Dfile.encoding=UTF-8")
    val raw = (project.findProperty("rawArgs") as String? ?: "")
    args(raw.split(" ").filter { it.isNotBlank() })
}

/** JM 源独立探针：gradle :probe:jmProbe -PjmArgs="<关键词>"（用内存存储，不需要 Android） */
tasks.register<JavaExec>("jmProbe") {
    group = "verification"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.tricomix.probe.JmProbeKt")
    jvmArgs("-Djava.net.preferIPv4Stack=true", "-Dfile.encoding=UTF-8")
    val a = (project.findProperty("jmArgs") as String? ?: "")
    args(a.split(" ").filter { it.isNotBlank() })
}

/** 定向 EH 探针：gradle :probe:ehProbe -PehArgs="<gid> <token>" */
tasks.register<JavaExec>("ehProbe") {
    group = "verification"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.tricomix.probe.EhProbeKt")
    jvmArgs("-Djava.net.preferIPv4Stack=true", "-Dfile.encoding=UTF-8")
    val a = (project.findProperty("ehArgs") as String? ?: "")
    args(a.split(" ").filter { it.isNotBlank() })
}
