/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm.exception

import at.bitfire.dav4jvm.HttpUtils
import at.bitfire.dav4jvm.buildRequest
import at.bitfire.dav4jvm.createResponse
import io.kotest.core.spec.style.FunSpec
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import korlibs.time.DateTime
import korlibs.time.DateTimeTz
import korlibs.time.minutes
import kotlin.math.abs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

object ServiceUnavailableExceptionTest : FunSpec({

    val httpClient = HttpClient(MockEngine)

    fun buildResponse(headers: Headers = headersOf()) = httpClient.createResponse(
        buildRequest {
            url("http://www.example.com")
            method = HttpMethod.Get
        },
        HttpStatusCode.ServiceUnavailable.description("Try later")
    )

    fun withinTimeRange(d: DateTimeTz, seconds: Int): Boolean {
        val msCheck = d.local.unixMillisLong
        val msShouldBe = DateTime.nowUnixMillisLong() + seconds * 1000
        // assume max. 5 seconds difference for test running
        return abs(msCheck - msShouldBe) < 5000
    }

    test("testRetryAfter_NoTime") {
        val e = ServiceUnavailableException(buildResponse())
        assertNull(e.retryAfter)
    }

    test("testRetryAfter_Seconds") {
        val response = buildResponse(headersOf("Retry-After", "120"))
        val e = ServiceUnavailableException(response)
        assertNotNull(e.retryAfter)
        assertTrue(withinTimeRange(e.retryAfter!!, 120))
    }

    test("testRetryAfter_Date") {
        val t = (DateTime.now() + 30.minutes)
        val response = buildResponse(headersOf("Retry-After", HttpUtils.formatDate(t)))
        val e = ServiceUnavailableException(response)
        assertNotNull(e.retryAfter)
        assertTrue(withinTimeRange(e.retryAfter!!, 30 * 60))
    }

})