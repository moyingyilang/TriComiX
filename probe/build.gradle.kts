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
