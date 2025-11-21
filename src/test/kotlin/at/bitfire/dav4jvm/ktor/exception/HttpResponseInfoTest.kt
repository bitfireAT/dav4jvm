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

import at.bitfire.dav4jvm.Error
import at.bitfire.dav4jvm.Property
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.engine.mock.respondOk
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HttpResponseInfoTest {

    private val sampleUrl = "https://example.com"


    // requestExcerpt

    @Test
    fun `requestExcerpt (binary blob)`() = runTest {
        val mockEngine = MockEngine {
            respondError(HttpStatusCode.InternalServerError)
        }
        val httpClient = HttpClient(mockEngine)
        val response = httpClient.post(sampleUrl) {
            contentType(ContentType.Application.OctetStream)
            setBody(ByteArray(6))
        }
        val result = HttpResponseInfo.fromResponse(response)
        assertEquals("POST $sampleUrl\n\n<request body with 6 byte(s)>", result.requestExcerpt)
    }

    @Test
    fun `requestExcerpt (no body)`() = runTest {
        val mockEngine = MockEngine {
            respondError(HttpStatusCode.InternalServerError)
        }
        val httpClient = HttpClient(mockEngine)
        val response = httpClient.get(sampleUrl)
        val result = HttpResponseInfo.fromResponse(response)
        assertEquals("GET $sampleUrl", result.requestExcerpt)
    }

    @Test
    fun `requestExcerpt (text as ByteArrayContent)`() = runTest {
        val mockEngine = MockEngine {
            respondError(HttpStatusCode.InternalServerError)
        }
        val httpClient = HttpClient(mockEngine)
        val response = httpClient.post(sampleUrl) {
            contentType(ContentType.Text.Plain)
            setBody("Sample".toByteArray())
        }
        val result = HttpResponseInfo.fromResponse(response)
        assertEquals("POST $sampleUrl\n\nSample", result.requestExcerpt)
    }

    @Test
    fun `requestExcerpt (text as large TextContent)`() = runTest {
        val mockEngine = MockEngine {
            respondError(HttpStatusCode.InternalServerError)
        }
        val httpClient = HttpClient(mockEngine)
        val result = httpClient.post(sampleUrl) {
            contentType(ContentType.Text.CSS)
            setBody("*".repeat(HttpResponseInfo.MAX_EXCERPT_SIZE * 2))
        }.let { response ->
            HttpResponseInfo.fromResponse(response)
        }
        val truncatedText = "*".repeat(HttpResponseInfo.MAX_EXCERPT_SIZE)
        assertEquals("POST $sampleUrl\n\n$truncatedText", result.requestExcerpt)
    }


    // responseExcerpt

    @Test
    fun `responseExcerpt (already consumed)`() = runTest {
        val mockEngine = MockEngine {
            respondError(
                status = HttpStatusCode.NotFound,
                content = "Interesting details about error",
                headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Html.toString())
            )
        }
        val httpClient = HttpClient(mockEngine)
        httpClient.prepareGet(sampleUrl).execute { response ->
            response.bodyAsChannel()
            // body is now already consumed
            val result = HttpResponseInfo.fromResponse(response)
            assertNull(result.responseExcerpt)
        }
    }

    @Test
    fun `responseExcerpt (binary blob)`() = runTest {
        val mockEngine = MockEngine {
            respondError(
                status = HttpStatusCode.NotFound,
                content = "Evil binary data",
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.OctetStream.toString())
            )
        }
        val httpClient = HttpClient(mockEngine)
        val response = httpClient.get(sampleUrl)
        val result = HttpResponseInfo.fromResponse(response)
        assertNull(result.responseExcerpt)
    }

    @Test
    fun `responseExcerpt (HTML)`() = runTest {
        val mockEngine = MockEngine {
            respondError(
                status = HttpStatusCode.NotFound,
                content = "Interesting details about error",
                headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Html.toString())
            )
        }
        val httpClient = HttpClient(mockEngine)
        val response = httpClient.get(sampleUrl)
        val result = HttpResponseInfo.fromResponse(response)
        assertEquals("Interesting details about error", result.responseExcerpt)
    }

    @Test
    fun `responseExcerpt (HTML over responseBodyChannel)`() = runTest {
        val mockEngine = MockEngine {
            respondError(
                status = HttpStatusCode.NotFound,
                content = "Interesting details about error",
                headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Html.toString())
            )
        }
        val httpClient = HttpClient(mockEngine)
        httpClient.prepareGet(sampleUrl).execute { response ->
            val channel = response.bodyAsChannel()
            // body is now already consumed, but existing channel can be used
            val result = HttpResponseInfo.fromResponse(response, channel)
            assertEquals("Interesting details about error", result.responseExcerpt)
        }
    }

    @Test
    fun `responseExcerpt (large HTML)`() = runTest {
        val mockEngine = MockEngine {
            respondError(
                status = HttpStatusCode.NotFound,
                content = "0123456789".repeat(3 * 1024),
                headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Html.toString())
            )
        }
        val httpClient = HttpClient(mockEngine)
        val response = httpClient.get(sampleUrl)
        val result = HttpResponseInfo.fromResponse(response)
        assertEquals(
            "0123456789".repeat(2 * 1024),    // limited to 20 kB
            result.responseExcerpt
        )
    }

    @Test
    fun `responseExcerpt (no MIME type)`() = runTest {
        val mockEngine = MockEngine {
            respondOk(
                content = "Maybe evil binary data",
            )
        }
        val httpClient = HttpClient(mockEngine)
        val response = httpClient.get(sampleUrl)
        val result = HttpResponseInfo.fromResponse(response)
        assertNull(result.responseExcerpt)
    }

    @Test
    fun `responseExcerpt (XML with error elements)`() = runTest {
        val xml = """
            <?xml version="1.0" encoding="utf-8" ?>
            <D:error xmlns:D="DAV:">
                <D:lock-token-submitted>
                    <D:href>/locked/</D:href>
                </D:lock-token-submitted>
            </D:error>
            """.trimIndent()

        val mockEngine = MockEngine {
            respond(
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Xml.toString()),
                content = xml
            )
        }
        val httpClient = HttpClient(mockEngine)
        val response = httpClient.get(sampleUrl)
        val result = HttpResponseInfo.fromResponse(response)
        assertEquals(xml, result.responseExcerpt)
        assertEquals(
            listOf(
                Error(Property.Name("DAV:", "lock-token-submitted"))
            ),
            result.errors
        )
    }

}