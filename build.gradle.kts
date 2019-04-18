
object Libs {
    // okhttp HTTP library
    // We'll use 3.12 for now, but this branch won't receive feature updates anymore. Security
    // updates are limited to Dec 2020, so we'll have to update to 3.13 until then. On Android,
    // using 3.13 will raise the required SDK level to Android 5.
    const val okhttpVersion = "3.12.2"

    // XmlPullParser library
    const val xpp3Version = "1.1.6"
}

plugins {
    kotlin("jvm") version "1.3.30"

    id("com.github.kukuhyoniatmoko.buildconfigkotlin") version "1.0.5"
    id("org.jetbrains.dokka") version "0.9.17"
}

repositories {
    jcenter()
}

dependencies {
    implementation(kotlin("stdlib"))

    api("com.squareup.okio:okio:2.+")       // use Kotlin-friendly okhttp 2.x
    api("com.squareup.okhttp3:okhttp:${Libs.okhttpVersion}")

    implementation("org.ogce:xpp3:${Libs.xpp3Version}")

    testImplementation("com.squareup.okhttp3:mockwebserver:${Libs.okhttpVersion}")
}

buildConfigKotlin {
    sourceSet("main", Action {
        buildConfig(name = "okhttpVersion", value = Libs.okhttpVersion)
    })
}
