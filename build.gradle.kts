import org.jetbrains.dokka.gradle.DokkaTask
import java.net.URL

object Libs {
    // okhttp HTTP library
    const val okhttpVersion = "4.10.0"

    // XmlPullParser library
    const val xpp3Version = "1.1.6"
}

repositories {
    mavenCentral()
}

group="com.github.bitfireAT"
version="2.2"

plugins {
    kotlin("jvm") version "1.7.20"
    `maven-publish`

    id("org.jetbrains.dokka") version "1.7.20"
}

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
                remoteUrl.set(URL("https://gitlab.com/bitfireAT/dav4jvm/tree/master/src/main/kotlin"))
                remoteLineSuffix.set("#L")
            }
        }
    }
}

dependencies {
    implementation(kotlin("stdlib-jdk7"))

    api("com.squareup.okhttp3:okhttp:${Libs.okhttpVersion}")
    implementation("org.apache.commons:commons-lang3:3.8.1")    // last version that doesn't require Java 8
    api("org.ogce:xpp3:${Libs.xpp3Version}")

    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:${Libs.okhttpVersion}")
}
