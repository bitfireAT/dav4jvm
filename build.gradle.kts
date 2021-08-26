import org.jetbrains.dokka.gradle.DokkaTask
import java.net.URL

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

    id("org.jetbrains.dokka") version "1.5.0"
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
    implementation(kotlin("stdlib"))

    api("com.squareup.okhttp3:okhttp:${Libs.okhttpVersion}")
    implementation("org.apache.commons:commons-lang3:3.8.1")    // last version that doesn't require Java 8
    api("org.ogce:xpp3:${Libs.xpp3Version}")

    testImplementation("com.squareup.okhttp3:mockwebserver:${Libs.okhttpVersion}")
}
