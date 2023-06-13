import org.jetbrains.dokka.gradle.DokkaTask
import java.net.URL

object Libs {

    // ktor HTTP library
    const val ktorVersion = "2.3.1"

    // XmlUtil library
    const val xmlVersion = "0.86.0"

    const val kotestVersion = "5.6.2"

    const val klockVersion = "4.0.3"

}

repositories {
    mavenCentral()
}

group="com.github.bitfireAT"
version="2.2-mpp"

plugins {
    kotlin("multiplatform") version "1.8.21"
    id("io.kotest.multiplatform") version "5.6.2"
    `maven-publish`

    id("org.jetbrains.dokka") version "1.8.10"
}

/*publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }

    repositories {
        maven {
            name = "dav4jvm"
            url = uri(layout.buildDirectory.dir("repo"))
        }
    }
}*/

tasks.withType<DokkaTask>().configureEach {
    dokkaSourceSets {
        named("commonMain") {
            moduleName.set("dav4jvm")
            sourceLink {
                localDirectory.set(file("src/commonMain/kotlin"))
                remoteUrl.set(URL("https://github.com/bitfireAT/dav4jvm/tree/main/src/main/kotlin/"))
                remoteLineSuffix.set("#L")
            }
        }
    }
}


kotlin {
    jvm()
    linuxX64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib"))
                api("io.ktor:ktor-client-core:${Libs.ktorVersion}")
                api("io.ktor:ktor-client-auth:${Libs.ktorVersion}")
                implementation("io.github.pdvrieze.xmlutil:core:${Libs.xmlVersion}")
                implementation("com.soywiz.korlibs.klock:klock:${Libs.klockVersion}")
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("io.kotest:kotest-framework-engine:${Libs.kotestVersion}")
                implementation("io.kotest:kotest-framework-datatest:${Libs.kotestVersion}")
                implementation("io.kotest:kotest-assertions-core:${Libs.kotestVersion}")
                implementation("io.ktor:ktor-client-mock:${Libs.ktorVersion}")
                implementation("io.ktor:ktor-client-auth:${Libs.ktorVersion}")
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation("io.kotest:kotest-runner-junit5:${Libs.kotestVersion}")
            }
        }
    }
}

tasks.named<Test>("jvmTest") {
    useJUnitPlatform()
    filter {
        isFailOnNoMatchingTests = false
    }
    testLogging {
        showExceptions = true
        showStandardStreams = true
        events = setOf(
            org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED,
            org.gradle.api.tasks.testing.logging.TestLogEvent.PASSED
        )
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}