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

import io.ktor.client.plugins.auth.providers.BasicAuthCredentials
import io.ktor.client.plugins.auth.providers.BasicAuthProvider
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.http.HttpHeaders
import io.ktor.http.URLProtocol
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainAuthProviderTest {

    private fun domainAuthProvider(firstLevelDomain: String?) =
        DomainAuthProvider(
            firstLevelDomain,
            BasicAuthProvider(
                credentials = { BasicAuthCredentials("user", "password") },
                sendWithoutRequestCallback = { true }
            )
        )

    @Test
    fun `addRequestHeaders() with domain not set`() = runTest {
        val authProvider = domainAuthProvider(firstLevelDomain = null)
        val request = HttpRequestBuilder().apply {
            url.protocol = URLProtocol.HTTPS
            url.host = "domain.example"
        }

        authProvider.addRequestHeaders(request)

        assertEquals("Basic dXNlcjpwYXNzd29yZA==", request.headers[HttpHeaders.Authorization])
    }

    @Test
    fun `addRequestHeaders() with request hostname equal to domain`() = runTest {
        val authProvider = domainAuthProvider(firstLevelDomain = "domain.example")
        val request = HttpRequestBuilder().apply {
            url.protocol = URLProtocol.HTTPS
            url.host = "domain.example"
        }

        authProvider.addRequestHeaders(request)

        assertEquals("Basic dXNlcjpwYXNzd29yZA==", request.headers[HttpHeaders.Authorization])
    }

    @Test
    fun `addRequestHeaders() with request hostname case-insensitively matching domain`() = runTest {
        val authProvider = domainAuthProvider(firstLevelDomain = "domain.example")
        val request = HttpRequestBuilder().apply {
            url.protocol = URLProtocol.HTTPS
            url.host = "DOMAIN.example"
        }

        authProvider.addRequestHeaders(request)

        assertEquals("Basic dXNlcjpwYXNzd29yZA==", request.headers[HttpHeaders.Authorization])
    }

    @Test
    fun `addRequestHeaders() with request hostname being a subdomain of domain`() = runTest {
        val authProvider = domainAuthProvider(firstLevelDomain = "domain.example")
        val request = HttpRequestBuilder().apply {
            url.protocol = URLProtocol.HTTPS
            url.host = "subdomain.domain.example"
        }

        authProvider.addRequestHeaders(request)

        assertEquals("Basic dXNlcjpwYXNzd29yZA==", request.headers[HttpHeaders.Authorization])
    }

    @Test
    fun `addRequestHeaders() with request hostname not matching domain`() = runTest {
        val authProvider = domainAuthProvider(firstLevelDomain = "domain.example")
        val request = HttpRequestBuilder().apply {
            url.protocol = URLProtocol.HTTPS
            url.host = "other-domain.example"
        }

        authProvider.addRequestHeaders(request)

        assertNull(request.headers[HttpHeaders.Authorization])
    }

    @Test
    fun `sendWithoutRequest() without domain set`() = runTest {
        val authProvider = domainAuthProvider(firstLevelDomain = null)
        val request = HttpRequestBuilder().apply {
            url.protocol = URLProtocol.HTTPS
            url.host = "domain.example"
        }

        assertTrue(authProvider.sendWithoutRequest(request))
    }

    @Test
    fun `sendWithoutRequest() with request hostname equal to domain`() = runTest {
        val authProvider = domainAuthProvider(firstLevelDomain = "domain.example")
        val request = HttpRequestBuilder().apply {
            url.protocol = URLProtocol.HTTPS
            url.host = "domain.example"
        }

        assertTrue(authProvider.sendWithoutRequest(request))
    }

    @Test
    fun `sendWithoutRequest() with request hostname case-insensitively matching domain`() = runTest {
        val authProvider = domainAuthProvider(firstLevelDomain = "domain.example")
        val request = HttpRequestBuilder().apply {
            url.protocol = URLProtocol.HTTPS
            url.host = "DOMAIN.example"
        }

        assertTrue(authProvider.sendWithoutRequest(request))
    }

    @Test
    fun `sendWithoutRequest() with request hostname being a subdomain of domain`() = runTest {
        val authProvider = domainAuthProvider(firstLevelDomain = "domain.example")
        val request = HttpRequestBuilder().apply {
            url.protocol = URLProtocol.HTTPS
            url.host = "subdomain.domain.example"
        }

        assertTrue(authProvider.sendWithoutRequest(request))
    }

    @Test
    fun `sendWithoutRequest() with request hostname not matching domain`() = runTest {
        val authProvider = domainAuthProvider(firstLevelDomain = "domain.example")
        val request = HttpRequestBuilder().apply {
            url.protocol = URLProtocol.HTTPS
            url.host = "other-domain.example"
        }

        assertFalse(authProvider.sendWithoutRequest(request))
    }

}
