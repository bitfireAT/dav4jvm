import org.jetbrains.dokka.gradle.DokkaTask

object Libs {
    // okhttp HTTP library
    const val okhttpVersion = "4.9.1"

    // XmlPullParser library
    const val xpp3Version = "1.1.6"
}

repositories {
    mavenCentral()
}

group="com.gitlab.bitfireAT"
version="1.0"

plugins {
    kotlin("jvm") version "1.5.21"
    `maven-publish`

    //id("com.github.kukuhyoniatmoko.buildconfigkotlin") version "1.0.5"
    id("org.jetbrains.dokka") version "0.10.1"
}

tasks {
    val dokka by getting(DokkaTask::class) {
        configuration {
            sourceLink {
                url = "https://gitlab.com/bitfireAT/dav4jvm/tree/master/"
                lineSuffix = "#L"
            }
        }
    }
}

dependencies {
    implementation(kotlin("stdlib"))

    api("com.squareup.okhttp3:okhttp:${Libs.okhttpVersion}")
    implementation("org.apache.commons:commons-lang3:3.8.1")    // last version that doesn't require Java 8
    api("org.ogce:xpp3:${Libs.xpp3Version}")

    testImplementation("com.squareup.okhttp3:mockwebserver:${Libs.okhttpVersion}")
}
