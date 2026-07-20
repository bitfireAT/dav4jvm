/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

import org.jetbrains.dokka.gradle.DokkaTask
import java.net.URI

repositories {
    mavenCentral()
}

/** Checks whether this string is a valid semver: `X.Y.Z` */
fun String.isSemVer(): Boolean = "\\d+.\\d+.\\d+".toRegex().matches(this)

group="com.github.bitfireAT"

// set by jitpack.io
val gitCommit: String? = System.getenv("GIT_COMMIT")
val jitpackVersion: String? = System.getenv("VERSION")
version = if (jitpackVersion?.isSemVer() == true) jitpackVersion else gitCommit ?: "SNAPSHOT"

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
                remoteUrl.set(URI("https://github.com/bitfireAT/dav4jvm/tree/main/src/main/kotlin/").toURL())
                remoteLineSuffix.set("#L")
            }
        }
    }
}

dependencies {
    api(libs.kotlin.coroutines.core)
    api(libs.ktor.client.core)
    api(libs.spotbugs.annotations)
    api(libs.xpp3)

    implementation(libs.guava)
    implementation(libs.ktor.client.auth)
    implementation(libs.ktor.client.encoding)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlin.coroutines.test)
    testImplementation(libs.ktor.client.mock)
}