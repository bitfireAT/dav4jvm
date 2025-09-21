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
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HttpResponseInfoTest {

    private val sampleUrl = "https://example.com"

    // requestExcerpt

    @Test
    fun `requestExcerpt (binary blob)`() {

        val mockEngine = MockEngine { request ->
            respondError(
                status = HttpStatusCode.NoContent,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Plain.toString())
            )
        }
        val httpClient = HttpClient(mockEngine) {
            followRedirects = false
        }

        runBlocking {
            val response = httpClient.post(sampleUrl) {
                setBody("Sample")
                contentType(ContentType.parse("application/test"))
            }
            val result = HttpException(response, "Message")
            assertEquals("POST $sampleUrl\n\n<request body (6 bytes)>", result.requestExcerpt)
        }
    }

    @Test
    fun `requestExcerpt (large CSS text)`() {

        val mockEngine = MockEngine { request ->
            respondError(
                status = HttpStatusCode.NoContent,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Plain.toString())
            )
        }
        val httpClient = HttpClient(mockEngine) {
            followRedirects = false
        }

        runBlocking {
            val result = httpClient.post(sampleUrl) {
                setBody("*".repeat(DavException.Companion.MAX_EXCERPT_SIZE * 2))
                contentType(ContentType.Text.CSS)
            }.let { response ->
                HttpResponseInfo.fromResponse(response)
            }
            val truncatedText = "*".repeat(DavException.Companion.MAX_EXCERPT_SIZE)
            assertEquals("POST $sampleUrl\n\n$truncatedText", result.requestExcerpt)
        }
    }


    // responseExcerpt

    @Test
    fun `responseExcerpt (binary blob)`() {

        val mockEngine = MockEngine { request ->
            respondError(
                status = HttpStatusCode.NotFound,
                content = "Evil binary data",
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.OctetStream.toString())
            )
        }
        val httpClient = HttpClient(mockEngine) {
            followRedirects = false
        }

        runBlocking {
            val result = httpClient.get(sampleUrl).let { response ->
                HttpResponseInfo.Companion.fromResponse(response)
            }
            assertNull(result.responseExcerpt)
        }
    }

    @Test
    fun `responseExcerpt (HTML)`() {

        val mockEngine = MockEngine { request ->
            respondError(
                status = HttpStatusCode.NotFound,
                content = "Interesting details about error",
                headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Html.toString())
            )
        }
        val httpClient = HttpClient(mockEngine) {
            followRedirects = false
        }

        runBlocking {
            val result = httpClient.get(sampleUrl).let { response ->
                HttpResponseInfo.Companion.fromResponse(response)
            }
            assertEquals("Interesting details about error", result.responseExcerpt)
        }
    }

    @Test
    fun `responseExcerpt (large HTML)`() {

        val mockEngine = MockEngine { request ->
            respondError(
                status = HttpStatusCode.NotFound,
                content = "0123456789".repeat(3 * 1024),
                headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Html.toString())
            )
        }
        val httpClient = HttpClient(mockEngine) {
            followRedirects = false
        }

        runBlocking {
            val result = httpClient.get(sampleUrl).let { response ->
                HttpResponseInfo.Companion.fromResponse(response)
            }
            assertEquals(
                "0123456789".repeat(2 * 1024),    // limited to 20 kB
                result.responseExcerpt
            )
        }
    }


    @Test
    fun `responseExcerpt (no MIME type)`() {

        val mockEngine = MockEngine { request ->
            respondOk(
                content = "Maybe evil binary data",
            )
        }
        val httpClient = HttpClient(mockEngine) {
            followRedirects = false
        }

        runBlocking {
            val result = httpClient.get(sampleUrl).let { response ->
                HttpResponseInfo.Companion.fromResponse(response)
            }
            assertNull(result.responseExcerpt)
        }
    }

    @Test
    fun `responseExcerpt (XML with error elements)`() {

        val xml = """
            <?xml version="1.0" encoding="utf-8" ?>
            <D:error xmlns:D="DAV:">
                <D:lock-token-submitted>
                    <D:href>/locked/</D:href>
                </D:lock-token-submitted>
            </D:error>
            """.trimIndent()

        val mockEngine = MockEngine { request ->
            respond(
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Xml.toString()),
                content = xml
            )
        }
        val httpClient = HttpClient(mockEngine) {
            followRedirects = false
        }

        runBlocking {
            val result = httpClient.get(sampleUrl).let { response ->
                HttpResponseInfo.Companion.fromResponse(response)
            }
            assertEquals(xml, result.responseExcerpt)
            assertEquals(
                listOf(
                    Error(Property.Name("DAV:", "lock-token-submitted"))
                ),
                result.errors
            )
        }
    }
}