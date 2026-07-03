/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

package at.bitfire.dav4jvm.ktor

import io.ktor.client.plugins.auth.AuthProvider
import io.ktor.client.plugins.auth.providers.BasicAuthCredentials
import io.ktor.client.plugins.auth.providers.BasicAuthProvider

/**
 * Creates an [AuthProvider] to manage Basic authentication against a given service.
 *
 * Usage:
 * ```
 * install(Auth) {
 *     providers.add(
 *         createDomainBasicAuthProvider(
 *             username = "user",
 *             password = "password",
 *             firstLevelDomain = "domain.example",
 *         )
 *     )
 * }
 * ```
 */
fun createDomainBasicAuthProvider(
    username: String,
    password: String,
    firstLevelDomain: String? = null,
    insecurePreemptive: Boolean = false,
): DomainAuthProvider {
    val basicAuthProvider = BasicAuthProvider(
        credentials = { BasicAuthCredentials(username, password) },
        sendWithoutRequestCallback = { true }
    )

    return DomainAuthProvider(firstLevelDomain, insecurePreemptive, basicAuthProvider)
}
