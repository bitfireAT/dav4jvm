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
import io.ktor.http.HttpHeaders
import io.ktor.http.auth.AuthScheme
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.http.encodedPath

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
    private val digestAuthProvider by lazy {
        // DigestAuthProvider blocks the thread if the default dispatcher's pool is full.
        // See DigestAuthProviderCongestionTest for a test that reproduces this.
        // Then, in isApplicable, we only use it if necessary (basicAuthProvider is not applicable)
        // Bug report: https://youtrack.jetbrains.com/issue/KTOR-9722/DigestAuthProvider-cannot-be-initialized-with-a-congested-Dispatchers.Default-pool
        DigestAuthProvider(
            credentials = { DigestAuthCredentials(username, password) }
        )
    }

    /**
     *  The last Digest challenge received from the server, or null when Basic auth is to be used
     *  preemptively (the default, until the server challenges for Digest).
     *
     *  [digestAuthProvider] takes the realm straight from the challenge it is passed, because it never expects to be
     *  used preemptively (its sendWithoutRequest always returns false). We do use it preemptively though, so we have
     *  to replay the remembered challenge – otherwise it would compute HA1 over the literal string "null" and omit the
     *  realm parameter. Only the realm is taken from it; nonce/qop/opaque come from the state it stored in
     *  isApplicable().
     */
    @Volatile
    private var preemptiveDigestChallenge: HttpAuthHeader? = null

    @Suppress("OverridingDeprecatedMember", "DEPRECATION_ERROR")
    @Deprecated("Please use sendWithoutRequest function instead", level = DeprecationLevel.ERROR)
    override val sendWithoutRequest: Boolean
        get() = error("Deprecated")

    override fun sendWithoutRequest(request: HttpRequestBuilder): Boolean = true

    override fun isApplicable(auth: HttpAuthHeader): Boolean = when {
        // Check for basicAuthProvider first so that we do not construct digestAuthProvider if not needed
        basicAuthProvider.isApplicable(auth) -> {
            // server requested Basic auth, switch (back) to Basic auth
            preemptiveDigestChallenge = null
            true
        }

        digestAuthProvider.isApplicable(auth) -> {
            // server requested Digest auth, switch to Digest auth
            preemptiveDigestChallenge = auth
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

        /* On a retry, authHeader tells us exactly which scheme (basic vs digest) this response challenged for. Digest
        needs a challenge to read the realm from, see [preemptiveDigestChallenge]. Basic ignores it. */
        val challenge = authHeader ?: preemptiveDigestChallenge
        val provider =
            if (challenge != null && challenge.authScheme.equals(AuthScheme.Digest, ignoreCase = true)) {
                request.workaroundKtorEmptyDigestUri()
                digestAuthProvider
            } else
                basicAuthProvider
        provider.addRequestHeaders(request, challenge)
    }

}

/**
 * Workaround for KTOR-9760: ktor's [DigestAuthProvider] takes both the `uri` auth parameter and HA2 from
 * `Url.fullPath`, which is empty for a URL without a path (like `https://example.com`, where the trailing slash is
 * missing – as it can be entered by the user during login). It then sends `uri=""` and hashes over `"PROPFIND:"`,
 * while the request actually sent is `PROPFIND / HTTP/1.1` (required by RFC 9112 3.2.1) – so the server's hash can
 * never match ours.
 *
 * Normalizing the path here makes both agree, and doesn't change what is sent.
 *
 * Remove this function together with its call site as soon as the ktor bug is fixed. `KtorDigestEmptyPathTest` pins
 * the buggy ktor behavior and starts to fail once it is.
 *
 * @see <a href="https://youtrack.jetbrains.com/issue/KTOR-9760">ktor bug report</a>
 * @see <a href="https://github.com/bitfireAT/dav4jvm/issues/219">dav4jvm issue</a>
 */
private fun HttpRequestBuilder.workaroundKtorEmptyDigestUri() {
    if (url.encodedPath.isEmpty())
        url.encodedPath = "/"
}
