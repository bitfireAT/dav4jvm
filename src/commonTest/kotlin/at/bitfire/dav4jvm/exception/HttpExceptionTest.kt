/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm.exception

import at.bitfire.dav4jvm.buildRequest
import at.bitfire.dav4jvm.createMockClient
import at.bitfire.dav4jvm.createResponse
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.utils.io.core.*

object HttpExceptionTest : FunSpec({

    val responseMessage = "Unknown error"

    test("testHttpFormatting") {
        val request = buildRequest {
            method = HttpMethod.Post
            url("http://example.com")
            header(HttpHeaders.ContentType, "text/something")
            setBody(ByteArrayContent("REQUEST\nBODY".toByteArray()))
        }

        val response = createMockClient().createResponse(
            request,
            HttpStatusCode.InternalServerError.description(responseMessage),
            headersOf(HttpHeaders.ContentType, "text/something-other"),
            "SERVER\r\nRESPONSE"
        )
        val e = HttpException(response)
        e.message.shouldContain("500")
        e.message.shouldContain(responseMessage)
        e.requestBody.shouldContain("REQUEST\nBODY")
        e.responseBody.shouldContain("SERVER\r\nRESPONSE")
    }

})
