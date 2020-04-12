import org.jetbrains.dokka.gradle.DokkaTask

object Libs {
    // okhttp HTTP library
    const val okhttpVersion = "4.5.0"

    // XmlPullParser library
    const val xpp3Version = "1.1.6"
}

group="com.gitlab.bitfireAT"

repositories {
    jcenter()
}

plugins {
    kotlin("jvm") version "1.3.71"

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

    // okhttp
    api(platform("com.squareup.okhttp3:okhttp-bom:${Libs.okhttpVersion}"))
    api("com.squareup.okhttp3:okhttp")
    // use Kotlin-friendly okhttp 2.x
    implementation("com.squareup.okio:okio:2.+")

    implementation("org.apache.commons:commons-lang3:3.9")

    api("org.ogce:xpp3:${Libs.xpp3Version}")

    testImplementation("com.squareup.okhttp3:mockwebserver")
}

buildConfigKotlin {
    sourceSet("main", Action {
        buildConfig(name = "okhttpVersion", value = Libs.okhttpVersion)
    })
}
