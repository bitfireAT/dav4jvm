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

import io.ktor.client.plugins.auth.providers.DigestAuthCredentials
import io.ktor.client.plugins.auth.providers.DigestAuthProvider
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.http.auth.parseAuthorizationHeader
import io.ktor.http.fullPath
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the ktor behavior that `workaroundKtorEmptyDigestUri` in [PreemptiveBasicDigestAuthProvider] compensates for:
 * for a URL without a path, `Url.fullPath` is empty, and ktor's [DigestAuthProvider] signs that empty string instead
 * of the `/` that is actually sent as request target (RFC 9112 3.2.1).
 *
 * **When one of these tests fails, ktor has fixed the bug and the workaround can be dropped.**
 *
 * Note: this class constructs a [DigestAuthProvider], which warms up ktor's process-wide nonce buffer.
 * See [DigestAuthProviderCongestionTest] for why that matters there.
 *
 * @see <a href="https://youtrack.jetbrains.com/issue/KTOR-9760">ktor bug report</a>
 * @see <a href="https://github.com/bitfireAT/dav4jvm/issues/219">dav4jvm issue</a>
 */
class KtorDigestEmptyPathTest {

    /** The root cause, in `ktor-http`. */
    @Test
    fun `fullPath is empty for a URL without a path`() {
        val url = URLBuilder().apply {
            protocol = URLProtocol.HTTPS
            host = "domain.example"
        }.build()

        assertEquals(
            "KTOR-9760 is fixed (ktor normalizes the empty path now) – remove workaroundKtorEmptyDigestUri()",
            "",
            url.fullPath
        )
    }

    /** The symptom we actually suffer from, in `ktor-client-auth`. */
    @Test
    fun `DigestAuthProvider signs an empty uri for a URL without a path`() = runTest {
        val digestAuthProvider = DigestAuthProvider(
            credentials = { DigestAuthCredentials("user", "password") }
        )
        val authHeader = parseAuthorizationHeader("""Digest algorithm=MD5, realm="realm", nonce="md5-nonce"""")!!

        // Note: isApplicable() needs to be called before addRequestHeaders()
        assertTrue(digestAuthProvider.isApplicable(authHeader))

        // URL without any path, as it can be entered by the user during login
        val request = HttpRequestBuilder().apply {
            url.protocol = URLProtocol.HTTPS
            url.host = "domain.example"
        }

        digestAuthProvider.addRequestHeaders(request, authHeader)

        // the uri parameter must match the request target on the wire, which is "/" and not ""
        val digest =
            parseAuthorizationHeader(request.headers[HttpHeaders.Authorization]!!) as HttpAuthHeader.Parameterized
        assertEquals(
            "KTOR-9760 is fixed (ktor authenticates for the correct path now) – remove workaroundKtorEmptyDigestUri()",
            "",
            digest.parameter("uri")
        )
    }

}
