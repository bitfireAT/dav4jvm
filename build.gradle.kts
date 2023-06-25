import org.jetbrains.dokka.gradle.DokkaTask
import java.net.URL

object Libs {
    // Ktor HTTP library
    const val ktorVersion = "2.3.1"

    // okhttp HTTP library
    const val okhttpVersion = "4.11.0"

    // XmlPullParser library
    const val xpp3Version = "1.1.6"
}

repositories {
    mavenCentral()
}

group = "com.github.bitfireAT"
version = "2.2"

plugins {
    kotlin("jvm") version "1.8.21"
    `maven-publish`

    id("org.jetbrains.dokka") version "1.8.10"
}

apply(from = "$rootDir/ktlint.gradle.kts")

publishing {
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
}

tasks.withType<DokkaTask>().configureEach {
    dokkaSourceSets {
        named("main") {
            moduleName.set("dav4jvm")
            sourceLink {
                localDirectory.set(file("src/main/kotlin"))
                remoteUrl.set(URL("https://github.com/bitfireAT/dav4jvm/tree/main/src/main/kotlin/"))
                remoteLineSuffix.set("#L")
            }
        }
    }
}

dependencies {
    api("com.squareup.okhttp3:okhttp:${Libs.okhttpVersion}")
    implementation("org.apache.commons:commons-lang3:3.8.1") // last version that doesn't require Java 8
    implementation("io.github.pdvrieze.xmlutil:core:0.86.0")
    implementation("io.ktor:ktor-io:${Libs.ktorVersion}")

    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:${Libs.okhttpVersion}")
}
