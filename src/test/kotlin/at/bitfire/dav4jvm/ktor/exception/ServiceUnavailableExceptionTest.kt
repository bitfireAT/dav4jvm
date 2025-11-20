/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

package at.bitfire.dav4jvm.ktor.exception

import at.bitfire.dav4jvm.HttpUtils
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ServiceUnavailableExceptionTest {

    private val sampleUrl = Url("http://www.example.com")

    @Test
    fun testRetryAfter_NoTime() = runTest {
        val mockEngine = MockEngine {
            respondError(HttpStatusCode.ServiceUnavailable)  // 503
        }
        val httpClient = HttpClient(mockEngine)
        val response = httpClient.get(sampleUrl)
        val ex = HttpException.fromResponse(response) as ServiceUnavailableException
        assertNull(ex.retryAfter)
        assertNull(ex.retryAfterAbs)
    }

    @Test
    fun testRetryAfter_Seconds() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = "",
                status = HttpStatusCode.ServiceUnavailable,  // 503
                headers = headersOf(HttpHeaders.RetryAfter, "120")
            )
        }
        val httpClient = HttpClient(mockEngine)

        val response = httpClient.get(sampleUrl)
        val ex = HttpException.fromResponse(response) as ServiceUnavailableException
        assertNotNull(ex.retryAfter)
        assertTrue(withinTimeRange(ex.retryAfterAbs!!, 120))
    }

    @Test
    fun testRetryAfter_Date() = runTest {
        val after30min = Instant.now().plusSeconds(30*60)
        val mockEngine = MockEngine {
            respondError(
                status = HttpStatusCode.ServiceUnavailable,  // 503
                headers = headersOf(HttpHeaders.RetryAfter, HttpUtils.formatDate(after30min))
            )
        }
        val httpClient = HttpClient(mockEngine)

        val response = httpClient.get(sampleUrl)
        val ex = HttpException.fromResponse(response) as ServiceUnavailableException
        assertNotNull(ex.retryAfter)
        assertTrue(withinTimeRange(ex.retryAfterAbs!!, 30*60))
    }


    // helpers

    private fun withinTimeRange(d: Instant, seconds: Long) =
        d.isBefore(
        Instant.now()
            .plusSeconds(seconds)
            .plusSeconds(5)     // tolerance for test running
        )

}