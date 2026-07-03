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
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Note: This mostly tests the functionality of DomainAuthProvider
class DomainBasicAuthProviderTest {

    @Test
    fun `addRequestHeaders() with domain not set`() = runTest {
        val authProvider = createDomainBasicAuthProvider(
            username = "user",
            password = "password",
            firstLevelDomain = null
        )
        val httpRequestBuilder = HttpRequestBuilder().apply {
            url.protocol = URLProtocol.HTTPS
            url.host = "domain.example"
        }

        authProvider.addRequestHeaders(httpRequestBuilder)

        assertEquals("Basic dXNlcjpwYXNzd29yZA==", httpRequestBuilder.headers[HttpHeaders.Authorization])
    }

    @Test
    fun `addRequestHeaders() with request hostname equal to domain`() = runTest {
        val authProvider = createDomainBasicAuthProvider(
            username = "user",
            password = "password",
            firstLevelDomain = "domain.example"
        )
        val httpRequestBuilder = HttpRequestBuilder().apply {
            url.protocol = URLProtocol.HTTPS
            url.host = "domain.example"
        }

        authProvider.addRequestHeaders(httpRequestBuilder)

        assertEquals("Basic dXNlcjpwYXNzd29yZA==", httpRequestBuilder.headers[HttpHeaders.Authorization])
    }

    @Test
    fun `addRequestHeaders() with request hostname case-insensitively matching domain`() = runTest {
        val authProvider = createDomainBasicAuthProvider(
            username = "user",
            password = "password",
            firstLevelDomain = "domain.example"
        )
        val httpRequestBuilder = HttpRequestBuilder().apply {
            url.protocol = URLProtocol.HTTPS
            url.host = "DOMAIN.example"
        }

        authProvider.addRequestHeaders(httpRequestBuilder)

        assertEquals("Basic dXNlcjpwYXNzd29yZA==", httpRequestBuilder.headers[HttpHeaders.Authorization])
    }

    @Test
    fun `addRequestHeaders() with request hostname being a subdomain of domain`() = runTest {
        val authProvider = createDomainBasicAuthProvider(
            username = "user",
            password = "password",
            firstLevelDomain = "domain.example"
        )
        val httpRequestBuilder = HttpRequestBuilder().apply {
            url.protocol = URLProtocol.HTTPS
            url.host = "subdomain.domain.example"
        }

        authProvider.addRequestHeaders(httpRequestBuilder)

        assertEquals("Basic dXNlcjpwYXNzd29yZA==", httpRequestBuilder.headers[HttpHeaders.Authorization])
    }

    @Test
    fun `addRequestHeaders() with request hostname not matching domain`() = runTest {
        val authProvider = createDomainBasicAuthProvider(
            username = "user",
            password = "password",
            firstLevelDomain = "domain.example"
        )
        val httpRequestBuilder = HttpRequestBuilder().apply {
            url.protocol = URLProtocol.HTTPS
            url.host = "other-domain.example"
        }

        authProvider.addRequestHeaders(httpRequestBuilder)

        assertNull(httpRequestBuilder.headers[HttpHeaders.Authorization])
    }

    @Test
    fun `addRequestHeaders() with insecure protocol`() = runTest {
        val authProvider = createDomainBasicAuthProvider(
            username = "user",
            password = "password",
            firstLevelDomain = null
        )
        val httpRequestBuilder = HttpRequestBuilder().apply {
            url.protocol = URLProtocol.HTTP
            url.host = "domain.example"
        }

        authProvider.addRequestHeaders(httpRequestBuilder)

        assertEquals("Basic dXNlcjpwYXNzd29yZA==", httpRequestBuilder.headers[HttpHeaders.Authorization])
    }

    @Test
    fun `sendWithoutRequest() without domain set`() = runTest {
        val authProvider = createDomainBasicAuthProvider(
            username = "user",
            password = "password",
            firstLevelDomain = null
        )
        val httpRequestBuilder = HttpRequestBuilder().apply {
            url.protocol = URLProtocol.HTTPS
            url.host = "domain.example"
        }

        val result = authProvider.sendWithoutRequest(httpRequestBuilder)

        assertTrue(result)
    }

    @Test
    fun `sendWithoutRequest() with insecure protocol and insecurePreemptive=false`() = runTest {
        val authProvider = createDomainBasicAuthProvider(
            username = "user",
            password = "password",
            firstLevelDomain = null,
            insecurePreemptive = false
        )
        val httpRequestBuilder = HttpRequestBuilder().apply {
            url.protocol = URLProtocol.HTTP
            url.host = "domain.example"
        }

        val result = authProvider.sendWithoutRequest(httpRequestBuilder)

        assertFalse(result)
    }

    @Test
    fun `sendWithoutRequest() with insecure protocol and insecurePreemptive=true`() = runTest {
        val authProvider = createDomainBasicAuthProvider(
            username = "user",
            password = "password",
            firstLevelDomain = null,
            insecurePreemptive = true
        )
        val httpRequestBuilder = HttpRequestBuilder().apply {
            url.protocol = URLProtocol.HTTP
            url.host = "domain.example"
        }

        val result = authProvider.sendWithoutRequest(httpRequestBuilder)

        assertTrue(result)
    }

    @Test
    fun `sendWithoutRequest() with request hostname equal to domain`() = runTest {
        val authProvider = createDomainBasicAuthProvider(
            username = "user",
            password = "password",
            firstLevelDomain = "domain.example"
        )
        val httpRequestBuilder = HttpRequestBuilder().apply {
            url.protocol = URLProtocol.HTTPS
            url.host = "domain.example"
        }

        val result = authProvider.sendWithoutRequest(httpRequestBuilder)

        assertTrue(result)
    }

    @Test
    fun `sendWithoutRequest() with request hostname case-insensitively matching domain`() = runTest {
        val authProvider = createDomainBasicAuthProvider(
            username = "user",
            password = "password",
            firstLevelDomain = "domain.example"
        )
        val httpRequestBuilder = HttpRequestBuilder().apply {
            url.protocol = URLProtocol.HTTPS
            url.host = "DOMAIN.example"
        }

        val result = authProvider.sendWithoutRequest(httpRequestBuilder)

        assertTrue(result)
    }

    @Test
    fun `sendWithoutRequest() with request hostname being a subdomain of domain`() = runTest {
        val authProvider = createDomainBasicAuthProvider(
            username = "user",
            password = "password",
            firstLevelDomain = "domain.example"
        )
        val httpRequestBuilder = HttpRequestBuilder().apply {
            url.protocol = URLProtocol.HTTPS
            url.host = "subdomain.domain.example"
        }

        val result = authProvider.sendWithoutRequest(httpRequestBuilder)

        assertTrue(result)
    }

    @Test
    fun `sendWithoutRequest() with request hostname not matching domain`() = runTest {
        val authProvider = createDomainBasicAuthProvider(
            username = "user",
            password = "password",
            firstLevelDomain = "domain.example"
        )
        val httpRequestBuilder = HttpRequestBuilder().apply {
            url.protocol = URLProtocol.HTTPS
            url.host = "other-domain.example"
        }

        val result = authProvider.sendWithoutRequest(httpRequestBuilder)

        assertFalse(result)
    }
}
