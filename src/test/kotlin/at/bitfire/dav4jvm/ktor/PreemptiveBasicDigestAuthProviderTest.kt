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

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.http.HttpHeaders
import io.ktor.http.URLProtocol
import io.ktor.http.auth.parseAuthorizationHeader
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreemptiveBasicDigestAuthProviderTest {

    @Test
    fun `sendWithoutRequest() is true before any challenge`() {
        val authProvider = PreemptiveBasicDigestAuthProvider(
            username = "user",
            password = "password"
        )
        val request = HttpRequestBuilder().apply {
            url.protocol = URLProtocol.HTTPS
            url.host = "domain.example"
        }

        assertTrue(authProvider.sendWithoutRequest(request))
    }

    @Test
    fun `sendWithoutRequest() stays true after Digest challenge`() {
        val authProvider = PreemptiveBasicDigestAuthProvider(
            username = "user",
            password = "password"
        )
        val authHeader = parseAuthorizationHeader("""Digest algorithm=MD5, realm="realm", nonce="md5-nonce"""")!!
        authProvider.isApplicable(authHeader)

        val request = HttpRequestBuilder().apply {
            url.protocol = URLProtocol.HTTPS
            url.host = "domain.example"
        }

        assertTrue(authProvider.sendWithoutRequest(request))
    }

    @Test
    fun `addRequestHeaders() sends Basic before any challenge`() = runTest {
        val authProvider = PreemptiveBasicDigestAuthProvider(
            username = "user",
            password = "password"
        )
        val request = HttpRequestBuilder().apply {
            url.protocol = URLProtocol.HTTPS
            url.host = "domain.example"
        }

        authProvider.addRequestHeaders(request, authHeader = null)

        assertEquals("Basic dXNlcjpwYXNzd29yZA==", request.headers[HttpHeaders.Authorization])
    }

    @Test
    fun `addRequestHeaders() replaces stale Authorization header with Digest after Digest challenge`() = runTest {
        // https://github.com/bitfireAT/davx5-ose/issues/2632
        val authProvider = PreemptiveBasicDigestAuthProvider(
            username = "user",
            password = "password"
        )
        val authHeader = parseAuthorizationHeader("""Digest algorithm=MD5, realm="realm", nonce="md5-nonce"""")!!

        // Note: isApplicable() needs to be called before addRequestHeaders()
        assertTrue(authProvider.isApplicable(authHeader))

        // simulates ktor's Auth plugin copying the original request's headers (including a
        // preemptively-set Basic header) into the retried request before calling addRequestHeaders()
        val request = HttpRequestBuilder().apply {
            url.protocol = URLProtocol.HTTPS
            url.host = "domain.example"
            headers.append(HttpHeaders.Authorization, "Basic dXNlcjpwYXNzd29yZA==")
        }

        authProvider.addRequestHeaders(request, authHeader)

        val authorizationHeaders = request.headers.getAll(HttpHeaders.Authorization)!!
        assertEquals(1, authorizationHeaders.size)
        assertTrue(authorizationHeaders.single().startsWith("Digest"))
    }

    @Test
    fun `addRequestHeaders() sends Digest preemptively on next request after Digest challenge`() = runTest {
        val authProvider = PreemptiveBasicDigestAuthProvider(
            username = "user",
            password = "password"
        )
        val authHeader = parseAuthorizationHeader("""Digest algorithm=MD5, realm="realm", nonce="md5-nonce"""")!!
        authProvider.isApplicable(authHeader)

        // a brand-new request, with no prior Authorization header and no fresh challenge
        val request = HttpRequestBuilder().apply {
            url.protocol = URLProtocol.HTTPS
            url.host = "domain.example"
        }

        authProvider.addRequestHeaders(request, authHeader = null)

        val authorizationHeaders = request.headers.getAll(HttpHeaders.Authorization)!!
        assertEquals(1, authorizationHeaders.size)
        assertTrue("""nonce="md5-nonce"""" in authorizationHeaders.single())
    }

}
