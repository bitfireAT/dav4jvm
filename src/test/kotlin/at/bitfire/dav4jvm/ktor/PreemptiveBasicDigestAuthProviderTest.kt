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
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.http.auth.parseAuthorizationHeader
import io.ktor.http.encodedPath
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
    fun `addRequestHeaders() derives scheme from authHeader instead of a concurrently-changed preemptive scheme`() =
        runTest {
            // simulates two concurrent requests on the same provider instance: this request's own
            // challenge is Digest, but a concurrent request's isApplicable() call flips the shared
            // preemptive-scheme state to Basic in between. The retry must still use Digest, since
            // that's what this response actually challenged for.
            val authProvider = PreemptiveBasicDigestAuthProvider(
                username = "user",
                password = "password"
            )
            val digestAuthHeader =
                parseAuthorizationHeader("""Digest algorithm=MD5, realm="realm", nonce="md5-nonce"""")!!
            assertTrue(authProvider.isApplicable(digestAuthHeader))

            // a concurrent request's own 401 handling runs isApplicable() with a Basic challenge,
            // flipping the shared preemptive-scheme state
            val basicAuthHeader = parseAuthorizationHeader("""Basic realm="realm"""")!!
            assertTrue(authProvider.isApplicable(basicAuthHeader))

            // this request's own retry, using its own (Digest) authHeader captured before the race
            val request = HttpRequestBuilder().apply {
                url.protocol = URLProtocol.HTTPS
                url.host = "domain.example"
            }

            authProvider.addRequestHeaders(request, digestAuthHeader)

            assertTrue(request.headers[HttpHeaders.Authorization]!!.startsWith("Digest"))
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
        val digest = parseAuthorizationHeader(authorizationHeaders.single()) as HttpAuthHeader.Parameterized
        // the realm has to be taken from the remembered challenge, and must not be null
        assertEquals("realm", digest.parameter("realm"))
        assertEquals("md5-nonce", digest.parameter("nonce"))
    }

    @Test
    fun `addRequestHeaders() authenticates for slash when the URL has no path`() = runTest {
        val authProvider = PreemptiveBasicDigestAuthProvider(
            username = "user",
            password = "password"
        )
        val authHeader = parseAuthorizationHeader("""Digest algorithm=MD5, realm="realm", nonce="md5-nonce"""")!!
        assertTrue(authProvider.isApplicable(authHeader))

        // URL without any path, as it can be entered by the user during login
        val request = HttpRequestBuilder().apply {
            url.protocol = URLProtocol.HTTPS
            url.host = "domain.example"
        }

        authProvider.addRequestHeaders(request, authHeader)

        // the request line on the wire is "GET / HTTP/1.1", so we have to authenticate for "/" and not for ""
        val digest =
            parseAuthorizationHeader(request.headers[HttpHeaders.Authorization]!!) as HttpAuthHeader.Parameterized
        assertEquals("/", digest.parameter("uri"))
    }

    @Test
    fun `addRequestHeaders() leaves the URL untouched for Basic`() = runTest {
        // the empty-path normalization is a workaround for a ktor Digest bug, so it must not affect Basic requests
        val authProvider = PreemptiveBasicDigestAuthProvider(
            username = "user",
            password = "password"
        )
        val request = HttpRequestBuilder().apply {
            url.protocol = URLProtocol.HTTPS
            url.host = "domain.example"
        }

        authProvider.addRequestHeaders(request, authHeader = null)

        assertEquals("", request.url.encodedPath)
    }

}
