import org.jetbrains.dokka.gradle.DokkaTask

object Libs {
    // okhttp HTTP library
    // We'll use 3.12 for now, but this branch won't receive feature updates anymore. Security
    // updates are limited to Dec 2020, so we'll have to update to 3.13 until then. On Android,
    // using 3.13 will raise the required SDK level to Android 5.
    const val okhttpVersion = "3.12.6"

    // XmlPullParser library
    const val xpp3Version = "1.1.6"
}

group="com.gitlab.bitfireAT"

repositories {
    jcenter()
}

plugins {
    kotlin("jvm") version "1.3.50"

    id("com.github.kukuhyoniatmoko.buildconfigkotlin") version "1.0.5"
    id("org.jetbrains.dokka") version "0.10.0"
    maven
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

    // use Kotlin-friendly okhttp 2.x
    implementation("com.squareup.okio:okio:2.+")
    api("com.squareup.okhttp3:okhttp:${Libs.okhttpVersion}")

    api("org.ogce:xpp3:${Libs.xpp3Version}")

    testImplementation("com.squareup.okhttp3:mockwebserver:${Libs.okhttpVersion}")
}

buildConfigKotlin {
    sourceSet("main", Action {
        buildConfig(name = "okhttpVersion", value = Libs.okhttpVersion)
    })
}
