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
import io.ktor.client.plugins.auth.providers.DigestAuthCredentials
import io.ktor.client.plugins.auth.providers.DigestAuthProvider
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.auth.HttpAuthHeader

/**
 * An [AuthProvider] that tries Basic auth preemptively and switches to Digest auth (remembered
 * for subsequent requests) once the server challenges for it.
 *
 * Handling both schemes in one provider avoids sending both an `Authorization: Basic` and
 * `Authorization: Digest` header on the same request (https://github.com/bitfireAT/dav4jvm/issues/198).
 *
 * Wrap in [DomainAuthProvider] to restrict it to a certain domain.
 *
 * @param username the username to authenticate with
 * @param password the password to authenticate with
 */
class PreemptiveBasicDigestAuthProvider(
    username: String,
    password: String
) : AuthProvider {

    private val basicAuthProvider = BasicAuthProvider(
        credentials = { BasicAuthCredentials(username, password) }
    )
    private val digestAuthProvider = DigestAuthProvider(
        credentials = { DigestAuthCredentials(username, password) }
    )

    /* Basic is used by default; switched to Digest once a WWW-Authenticate challenge from the server requests that. */
    @Volatile
    private var activeProvider: AuthProvider = basicAuthProvider

    @Suppress("OverridingDeprecatedMember", "DEPRECATION_ERROR")
    @Deprecated("Please use sendWithoutRequest function instead", level = DeprecationLevel.ERROR)
    override val sendWithoutRequest: Boolean
        get() = error("Deprecated")

    override fun sendWithoutRequest(request: HttpRequestBuilder): Boolean = true

    override fun isApplicable(auth: HttpAuthHeader): Boolean = when {
        digestAuthProvider.isApplicable(auth) -> {
            // server requested Digest auth, switch to Digest auth
            activeProvider = digestAuthProvider
            true
        }

        basicAuthProvider.isApplicable(auth) -> {
            // server requested Basic auth, switch (back) to Basic auth
            activeProvider = basicAuthProvider
            true
        }

        else -> {
            // neither Basic nor Digest requested, so this provider is not applicable
            false
        }
    }

    override suspend fun addRequestHeaders(request: HttpRequestBuilder, authHeader: HttpAuthHeader?) {
        /* Ktor's Auth plugin retry logic copies all headers from the original request (which
        may include a stale Authorization header set by a previous preemptive attempt)
        before calling this method. Always clear it first so we never send two Authorization headers. */
        request.headers.remove(HttpHeaders.Authorization)

        activeProvider.addRequestHeaders(request, authHeader)
    }

    override suspend fun refreshToken(response: HttpResponse): Boolean =
        activeProvider.refreshToken(response)

    override fun clearToken() {
        basicAuthProvider.clearToken()
        digestAuthProvider.clearToken()
        activeProvider = basicAuthProvider
    }

}
