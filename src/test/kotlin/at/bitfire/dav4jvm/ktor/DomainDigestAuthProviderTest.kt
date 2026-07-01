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
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainDigestAuthProviderTest {

    @Test
    fun happyPath() = runTest {
        val authProvider = createDomainDigestAuthProvider(
            username = "user",
            password = "password",
            domain = null
        )
        val httpRequestBuilder = HttpRequestBuilder().apply {
            url.protocol = URLProtocol.HTTPS
            url.host = "domain.example"
        }
        val authHeader = parseAuthorizationHeader("""Digest algorithm=MD5, realm="realm", nonce="md5-nonce"""")!!

        // Note: isApplicable() needs to be called before addRequestHeaders()
        val isApplicable = authProvider.isApplicable(authHeader)

        assertTrue(isApplicable)

        authProvider.addRequestHeaders(httpRequestBuilder, authHeader)

        assertTrue("""nonce="md5-nonce"""" in httpRequestBuilder.headers[HttpHeaders.Authorization]!!)
    }
}
