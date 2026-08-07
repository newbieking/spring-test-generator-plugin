plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.22"
    id("org.jetbrains.intellij") version "1.17.3"
}

group = "com.newbieking"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.google.code.gson:gson:2.10.1")
    testImplementation(kotlin("test"))
    testImplementation("io.mockk:mockk:1.13.9")
}

intellij {
    version.set("2023.3")
    type.set("IC") // 社区版，但支持所有旗舰特性
    plugins.set(listOf("com.intellij.java", "org.jetbrains.kotlin"))
}

tasks {
    runIde {
        systemProperty("idea.log.debug.categories", "com.newbieking.springtestgen")
    }

    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }
    withType<Test> {
        useJUnitPlatform()
    }

    patchPluginXml {
        sinceBuild.set("233")    // 2023.3
        untilBuild.set("261.*") // 2026.1
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }

    runIde {
        jvmArgs("-Didea.log.debug.categories=#com.newbieking")
    }
}
