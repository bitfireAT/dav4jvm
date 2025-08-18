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

import at.bitfire.dav4jvm.ktor.DavResource
import at.bitfire.dav4jvm.ktor.Property
import at.bitfire.dav4jvm.ktor.property.webdav.NS_WEBDAV
import at.bitfire.dav4jvm.ktor.property.webdav.ResourceType
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.prepareRequest
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import io.ktor.http.withCharset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

class DavExceptionTest {

    val sampleUrl = Url("https://127.0.0.1/dav/")


    /**
     * Test truncation of a too large plain text request in [DavException].
     */
    @Test
    fun testRequestLargeTextError() {
        val mockEngine = MockEngine { request ->
            respond(
                content = "",
                status = HttpStatusCode.NoContent,  // 204 No content
                headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Plain.toString())
            )
        }
        val httpClient = HttpClient(mockEngine) {
            followRedirects = false
        }

        val builder = StringBuilder()
        builder.append(CharArray(DavException.MAX_EXCERPT_SIZE+100) { '*' })
        val body = builder.toString()

        runBlocking {
            httpClient.prepareRequest(sampleUrl) {
                method = HttpMethod.Post
                headers.append(HttpHeaders.ContentType, ContentType.Text.Plain.toString())
                setBody(body)
            }.execute { response ->
                val e = DavException("Error with large request body", null, response)
                assertTrue(e.errors.isEmpty())
                assertEquals(
                    body.substring(0, DavException.MAX_EXCERPT_SIZE),
                    e.requestBody
                )
            }
        }
    }


    /**
     * Test a large HTML response which has a multi-octet UTF-8 character
     * exactly at the cut-off position.
     */
    @Test
    fun testResponseLargeTextError() {

        val builder = StringBuilder()
        builder.append(CharArray(DavException.MAX_EXCERPT_SIZE-1) { '*' })
        builder.append("\u03C0")    // Pi
        val body = builder.toString()

        val mockEngine = MockEngine { request ->
            respond(
                content = body,
                status = HttpStatusCode.NotFound,  // 404
                headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Html.toString())
            )
        }
        val httpClient = HttpClient(mockEngine) {
            followRedirects = false
        }

        val dav = DavResource(httpClient, sampleUrl)

        runBlocking {
            try {
                dav.propfind(0, ResourceType.NAME) { _, _ -> }
                fail("Expected HttpException")
            } catch (e: HttpException) {
                assertEquals(HttpStatusCode.NotFound.value, e.code)
                assertTrue(e.errors.isEmpty())
                assertEquals(
                    body.substring(0, DavException.MAX_EXCERPT_SIZE - 1),
                    e.responseBody!!.substring(0, DavException.MAX_EXCERPT_SIZE - 1)
                )
            }
        }
    }


    @Test
    fun testResponseNonTextError() {

        val mockEngine = MockEngine { request ->
            respond(
                content = "12345",
                status = HttpStatusCode.Forbidden,  // 404
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.OctetStream.toString())
            )
        }
        val httpClient = HttpClient(mockEngine) {
            followRedirects = false
        }

        val dav = DavResource(httpClient, sampleUrl)

        runBlocking {
            try {
                dav.propfind(0, ResourceType.NAME) { _, _ -> }
                fail("Expected HttpException")
            } catch (e: HttpException) {
                assertEquals(HttpStatusCode.Forbidden.value, e.code)
                assertTrue(e.errors.isEmpty())
                assertNull(e.responseBody)
            }
        }
    }


    @Test
    fun testSerialization() {

        val mockEngine = MockEngine { request ->
            respond(
                content = "12345",
                status = HttpStatusCode.InternalServerError,  // 500
                headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Plain.toString())
            )
        }
        val httpClient = HttpClient(mockEngine) {
            followRedirects = false
        }
        val dav = DavResource(httpClient, sampleUrl)

        runBlocking {
            try {
                dav.propfind(0, ResourceType.NAME) { _, _ -> }
                fail("Expected DavException")
            } catch (e: DavException) {
                val baos = ByteArrayOutputStream()
                val oos = ObjectOutputStream(baos)
                oos.writeObject(e)

                val ois = ObjectInputStream(ByteArrayInputStream(baos.toByteArray()))
                val e2 = ois.readObject() as HttpException
                assertEquals(HttpStatusCode.InternalServerError.value, e2.code)
                assertTrue(e2.responseBody!!.contains("12345"))
            }
        }
    }


    /**
     * Test precondition XML element (sample from RFC 4918 16)
     */
    @Test
    fun testXmlError() {

        val body = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
                "<D:error xmlns:D=\"DAV:\">\n" +
                "  <D:lock-token-submitted>\n" +
                "    <D:href>/workspace/webdav/</D:href>\n" +
                "  </D:lock-token-submitted>\n" +
                "</D:error>\n"

        val mockEngine = MockEngine { request ->
            respond(
                content = body,
                status = HttpStatusCode.Locked,  // 423
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Xml.withCharset(Charsets.UTF_8) .toString())
            )
        }
        val httpClient = HttpClient(mockEngine) {
            followRedirects = false
        }
        val dav = DavResource(httpClient, sampleUrl)

        runBlocking {
            try {
                dav.propfind(0, ResourceType.NAME) { _, _ -> }
                fail("Expected HttpException")
            } catch (e: HttpException) {
                assertEquals(HttpStatusCode.Locked.value, e.code)
                assertTrue(e.errors.any { it.name == Property.Name(NS_WEBDAV, "lock-token-submitted") })
                assertEquals(body, e.responseBody)
            }
        }
    }
}