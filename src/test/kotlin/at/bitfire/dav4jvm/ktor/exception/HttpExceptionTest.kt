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

import at.bitfire.dav4jvm.ktor.exception.HttpException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.prepareRequest
import io.ktor.client.request.setBody
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpExceptionTest {

    private val responseMessage = "Unknown error"

    @Test
    fun testHttpFormatting() {

        val mockEngine = MockEngine { request ->
            respond(
                content = "SERVER\r\nRESPONSE",
                status = HttpStatusCode(500, responseMessage),
                headers = headersOf(HttpHeaders.ContentType, "text/something")
            )
        }
        val httpClient = HttpClient(mockEngine)

        runBlocking {
            httpClient.prepareRequest("http://example.com") {
                setBody("REQUEST\nBODY")
            }.execute { response ->
                val e = HttpException(response)
                assertTrue(e.message!!.contains("500"))
                assertTrue(e.message!!.contains(responseMessage))
                assertTrue(e.responseBody!!.contains("SERVER\r\nRESPONSE"))
                assertTrue(e.requestBody!!.contains("REQUEST\nBODY"))
            }
        }
    }
}
