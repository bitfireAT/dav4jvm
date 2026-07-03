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
import io.ktor.client.plugins.auth.providers.DigestAuthCredentials
import io.ktor.client.plugins.auth.providers.DigestAuthProvider

/**
 * Creates an [AuthProvider] to manage Digest authentication against a given service.
 *
 * Usage:
 * ```
 * install(Auth) {
 *     providers.add(
 *         createDomainDigestAuthProvider(
 *             username = "user",
 *             password = "password",
 *             firstLevelDomain = "domain.example",
 *         )
 *     )
 * }
 * ```
 */
fun createDomainDigestAuthProvider(
    username: String,
    password: String,
    firstLevelDomain: String? = null,
): DomainAuthProvider {
    val digestAuthProvider = DigestAuthProvider(
        credentials = { DigestAuthCredentials(username, password) }
    )

    return DomainAuthProvider(firstLevelDomain, insecurePreemptive = false, digestAuthProvider)
}
