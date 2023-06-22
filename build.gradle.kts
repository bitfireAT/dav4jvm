import org.jetbrains.dokka.gradle.DokkaTask
import java.net.URL

repositories {
    mavenCentral()
}

group="com.github.bitfireAT"
version="2.2"

plugins {
    alias(libs.plugins.kotlin.jvm)
    `maven-publish`

    alias(libs.plugins.dokka)
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
                remoteUrl.set(URL("https://github.com/bitfireAT/dav4jvm/tree/main/src/main/kotlin/"))
                remoteLineSuffix.set("#L")
            }
        }
    }
}

dependencies {
    api(libs.okhttp)
    implementation(libs.commons.lang3)
    api(libs.xpp3)

    testImplementation(libs.junit4)
    testImplementation(libs.okhttp.mockwebserver)
}