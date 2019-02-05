
object Libs {
    const val okhttpVersion = "3.12.1"
    const val xpp3Version = "1.1.6"
}

plugins {
    kotlin("jvm") version "1.3.11"

    id("com.github.kukuhyoniatmoko.buildconfigkotlin") version "1.0.5"
    id("org.jetbrains.dokka") version "0.9.17"
}

repositories {
    jcenter()
}

dependencies {
    implementation(kotlin("stdlib"))

    api("com.squareup.okio:okio:2.+")
    api("com.squareup.okhttp3:okhttp:${Libs.okhttpVersion}")
    implementation("org.ogce:xpp3:${Libs.xpp3Version}")       // XmlPullParser

    testImplementation("com.squareup.okhttp3:mockwebserver:${Libs.okhttpVersion}")
}

buildConfigKotlin {
    sourceSet("main", Action {
        buildConfig(name = "okhttpVersion", value = Libs.okhttpVersion)
    })
}
