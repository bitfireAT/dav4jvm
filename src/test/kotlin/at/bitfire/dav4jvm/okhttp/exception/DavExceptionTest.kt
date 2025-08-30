/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

package at.bitfire.dav4jvm.okhttp.exception

import at.bitfire.dav4jvm.okhttp.Error
import at.bitfire.dav4jvm.okhttp.Property
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

class DavExceptionTest {

    private val client = OkHttpClient.Builder()
            .followRedirects(false)
            .build()
    private val mockServer = MockWebServer()

    @Before
    fun startServer() = mockServer.start()

    @After
    fun stopServer() = mockServer.close()


    @Test
    fun `Construct from closed response`() {
        mockServer.enqueue(MockResponse(
            code = 404,
            body = "Page not found"
        ))
        val response = client.newCall(Request.Builder()
            .get()
            .url(mockServer.url("/"))
            .build()).execute()
        response.close()

        val result = DavException("Test", response)
        assertNull(result.responseExcerpt)
    }

    @Test
    fun `requestExcerpt (binary blob)`() {
        mockServer.enqueue(MockResponse(
            code = 404,
            body = "Page not found"
        ))
        val url = mockServer.url("/")
        client.newCall(Request.Builder()
            .url(url)
            .post("Sample".toRequestBody("application/test".toMediaType()))
            .build()
        ).execute().use { response ->
            val result = DavException("Test", response)
            assertEquals("POST $url\n\n<request body>", result.requestExcerpt)
        }
    }

    @Test
    fun `requestExcerpt (large CSS text)`() {
        mockServer.enqueue(MockResponse(
            code = 404,
            body = "Page not found"
        ))
        val url = mockServer.url("/")
        client.newCall(Request.Builder()
            .url(url)
            .post("*".repeat(DavException.MAX_EXCERPT_SIZE * 2).toRequestBody("text/css".toMediaType()))
            .build()
        ).execute().use { response ->
            val result = DavException("Test", response)
            val truncatedText = "*".repeat(DavException.MAX_EXCERPT_SIZE)
            assertEquals("POST $url\n\n$truncatedText", result.requestExcerpt)
        }
    }

    @Test
    fun `responseExcerpt (binary blob)`() {
        mockServer.enqueue(MockResponse(
            code = 404,
            body = "Evil binary data",
            headers = Headers.headersOf("Content-Type", "application/octet-stream")
        ))
        val url = mockServer.url("/")
        client.newCall(Request.Builder()
            .url(url)
            .get()
            .build()
        ).execute().use { response ->
            val result = DavException("Test", response)
            assertNull(result.responseExcerpt)
        }
    }

    @Test
    fun `responseExcerpt (HTML)`() {
        mockServer.enqueue(MockResponse(
            code = 404,
            body = "Interesting details about error",
            headers = Headers.headersOf("Content-Type", "text/html")
        ))
        val url = mockServer.url("/")
        client.newCall(Request.Builder()
            .url(url)
            .get()
            .build()
        ).execute().use { response ->
            val result = DavException("Test", response)
            assertEquals("Interesting details about error", result.responseExcerpt)
        }
    }

    @Test
    fun `responseExcerpt (large HTML)`() {
        mockServer.enqueue(MockResponse(
            code = 404,
            body = "0123456789".repeat(3*1024), // 30 kB
            headers = Headers.headersOf("Content-Type", "text/html")
        ))
        val url = mockServer.url("/")
        client.newCall(Request.Builder()
            .url(url)
            .get()
            .build()
        ).execute().use { response ->
            val result = DavException("Test", response)
            assertEquals(
                "0123456789".repeat(2*1024),    // limited to 20 kB
                result.responseExcerpt
            )
        }
    }

    @Test
    fun `responseExcerpt (no Content-Type)`() {
        mockServer.enqueue(MockResponse(
            code = 404,
            body = "Maybe evil binary data"
        ))
        val url = mockServer.url("/")
        client.newCall(Request.Builder()
            .url(url)
            .get()
            .build()
        ).execute().use { response ->
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
        mockServer.enqueue(MockResponse(
            code = 404,
            body = xml,
            headers = Headers.headersOf("Content-Type", "application/xml")
        ))
        val url = mockServer.url("/")
        client.newCall(Request.Builder()
            .url(url)
            .get()
            .build()
        ).execute().use { response ->
            val result = DavException("Test", response)
            assertEquals(xml, result.responseExcerpt)
            assertEquals(
                listOf(
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