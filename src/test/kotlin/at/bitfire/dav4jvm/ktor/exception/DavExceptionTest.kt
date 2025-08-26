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

import at.bitfire.dav4jvm.ktor.Error
import at.bitfire.dav4jvm.ktor.Property
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

class DavExceptionTest {

    val sampleUrl = Url("https://127.0.0.1/dav/")

    @Test
    fun `Construct from closed response`() {
        val mockEngine = MockEngine { request ->
            respondError(
                content = "Page not found",
                status = HttpStatusCode.NotFound,  // 404
            )
        }
        val httpClient = HttpClient(mockEngine) {
            followRedirects = false
        }

        runBlocking {
            val response = httpClient.get(sampleUrl)
            val result = DavException("Test", response)
            assertNull(result.responseExcerpt)
        }
    }

    @Test
    fun `requestExcerpt (binary blob)`() {
        val mockEngine = MockEngine { request ->
            respondError(
                content = "Page not found",
                status = HttpStatusCode.NotFound,  // 404
            )
        }
        val httpClient = HttpClient(mockEngine) {
            followRedirects = false
        }

        runBlocking {
            val response = httpClient.post(sampleUrl) {
                setBody("Sample")
                headers.append(HttpHeaders.ContentType, "application/test")
            }
            val result = DavException("Test", response)
            assertEquals("POST $sampleUrl\n\n<request body>", result.requestExcerpt)
        }
    }

    @Test
    fun `requestExcerpt (large CSS text)`() {
        val mockEngine = MockEngine { request ->
            respondError(
                content = "Page not found",
                status = HttpStatusCode.NotFound,  // 404
            )
        }
        val httpClient = HttpClient(mockEngine) {
            followRedirects = false
        }

        runBlocking {
            val response = httpClient.post(sampleUrl) {
                setBody("*".repeat(DavException.MAX_EXCERPT_SIZE * 2))
                headers.append(HttpHeaders.ContentType,  ContentType.Text.CSS.toString())
            }
            val result = DavException("Test", response)
            val truncatedText = "*".repeat(DavException.MAX_EXCERPT_SIZE)
            assertEquals("POST $sampleUrl\n\n$truncatedText", result.requestExcerpt)
        }
    }

    @Test
    fun `responseExcerpt (binary blob)`() {

        val mockEngine = MockEngine { request ->
            respondError(
                content = "Evil binary data",
                status = HttpStatusCode.NotFound,  // 404
                headers = headersOf("Content-Type", ContentType.Application.OctetStream.toString())
            )
        }
        val httpClient = HttpClient(mockEngine) {
            followRedirects = false
        }

        runBlocking {
            val response = httpClient.get(sampleUrl)
            val result = DavException("Test", response)
            assertNull(result.responseExcerpt)
        }
    }

    @Test
    fun `responseExcerpt (HTML)`() {

        val mockEngine = MockEngine { request ->
            respondError(
                content = "Interesting details about error",
                status = HttpStatusCode.NotFound,  // 404
                headers = headersOf("Content-Type", ContentType.Text.Html.toString())
            )
        }
        val httpClient = HttpClient(mockEngine) {
            followRedirects = false
        }

        runBlocking {
            val response = httpClient.get(sampleUrl)
            val result = DavException("Test", response)
            assertEquals("Interesting details about error", result.responseExcerpt)
        }

    }

    @Test
    fun `responseExcerpt (large HTML)`() {

        val mockEngine = MockEngine { request ->
            respondError(
                content = "0123456789".repeat(3*1024), // 30 kB
                status = HttpStatusCode.NotFound,  // 404
                headers = headersOf("Content-Type", ContentType.Text.Html.toString())
            )
        }
        val httpClient = HttpClient(mockEngine) {
            followRedirects = false
        }

        runBlocking {
            val response = httpClient.get(sampleUrl)
            val result = DavException("Test", response)
            assertEquals(
                "0123456789".repeat(2*1024),    // limited to 20 kB
                result.responseExcerpt
            )
        }
    }

    @Test
    fun `responseExcerpt (no Content-Type)`() {

        val mockEngine = MockEngine { request ->
            respondError(
                content = "Maybe evil binary data",
                status = HttpStatusCode.NotFound,  // 404
            )
        }
        val httpClient = HttpClient(mockEngine) {
            followRedirects = false
        }

        runBlocking {
            val response = httpClient.get(sampleUrl)
            val result = DavException("Test", response)
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
            </D:error>""".trimIndent()


        val mockEngine = MockEngine { request ->
            respondError(
                content = xml,
                status = HttpStatusCode.NotFound,  // 404
                headers = headersOf("Content-Type", ContentType.Application.Xml.toString())
            )
        }
        val httpClient = HttpClient(mockEngine) {
            followRedirects = false
        }

        runBlocking {
            val response = httpClient.get(sampleUrl)
            val result = DavException("Test", response)
            assertEquals(xml, result.responseExcerpt)
            assertEquals(listOf(
                Error(Property.Name("DAV:", "lock-token-submitted"))
                ),
                result.errors
            )
        }
    }

    @Test
    fun `is Java-serializable`() {
        val davException = DavException(
            message = "Some Error",
            statusCode = 500,
            requestExcerpt = "Request Body",
            responseExcerpt = "Response Body",
            errors = listOf(
                Error(Property.Name("Serialized", "Name"))
            )
        )

        // serialize (Java-style as in Serializable interface, not Kotlin serialization)
        val blob = ByteArrayOutputStream().use { baos ->
            ObjectOutputStream(baos).use { oos ->
                oos.writeObject(davException)
            }
            baos.toByteArray()
        }

        // deserialize
        ByteArrayInputStream(blob).use { bais ->
            ObjectInputStream(bais).use { ois ->
                val actual = ois.readObject() as DavException
                assertEquals(davException.message, actual.message)
                assertEquals(davException.statusCode, actual.statusCode)
                assertEquals(davException.requestExcerpt, actual.requestExcerpt)
                assertEquals(davException.responseExcerpt, actual.responseExcerpt)
                assertEquals(davException.errors, actual.errors)
            }
        }
    }
}