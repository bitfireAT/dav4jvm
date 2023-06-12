/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm.exception

import at.bitfire.dav4jvm.buildRequest
import at.bitfire.dav4jvm.createResponse
import io.kotest.core.spec.style.FunSpec
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlin.test.assertTrue

object HttpExceptionTest : FunSpec({

    val responseMessage = "Unknown error"

    test("testHttpFormatting") {
        val request = buildRequest {
            method = HttpMethod.Post
            url("http://example.com")
            header(HttpHeaders.ContentType, "text/something")
            setBody("\"REQUEST\\nBODY\"")
        }

        val response = HttpClient(MockEngine).createResponse(
            request,
            HttpStatusCode.InternalServerError.description(responseMessage),
            headersOf(HttpHeaders.ContentType, "text/something-other"),
            "SERVER\r\nRESPONSE"
        )
        val e = HttpException(response)
        assertTrue(e.message!!.contains("500"))
        assertTrue(e.message!!.contains(responseMessage))
        assertTrue(e.requestBody!!.contains("REQUEST\nBODY"))
        assertTrue(e.responseBody!!.contains("SERVER\r\nRESPONSE"))
    }

})
