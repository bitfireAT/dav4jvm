/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm.exception

import at.bitfire.dav4jvm.*
import at.bitfire.dav4jvm.property.ResourceType
import io.kotest.core.spec.style.FunSpec
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.utils.io.charsets.*
import nl.adaptivity.xmlutil.QName
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail


@OptIn(InternalAPI::class)
object DavExceptionTest : FunSpec({
    val sampleText = "SAMPLE RESPONSE"

    val sampleUrl = Url("http://mock-server.com/dav/")

    val httpClient = HttpClient(MockEngine) {
        followRedirects = false
    }

    /**
     * Test truncation of a too large plain text request in [DavException].
     */
    test("testRequestLargeTextError") {
        val url = sampleUrl
        val dav = DavResource(httpClient, url)

        val builder = StringBuilder()
        builder.append(CharArray(DavException.MAX_EXCERPT_SIZE + 100) { '*' })
        val body = builder.toString()

        val response = httpClient.createResponse(
            buildRequest {
                url("http://example.com")
                method = HttpMethod.Post
                header(HttpHeaders.ContentType, ContentType.Text.Plain.toString())
            },
            HttpStatusCode.NoContent
        )

        val e = DavException("Error with large request body", null, response)

        assertTrue(e.errors.isEmpty())
        assertEquals(
            body.substring(0, DavException.MAX_EXCERPT_SIZE),
            e.requestBody
        )
    }

    /**
     * Test a large HTML response which has a multi-octet UTF-8 character
     * exactly at the cut-off position.
     */
    test("testResponseLargeTextError") {
        val url = sampleUrl
        val dav = DavResource(httpClient, url)

        val builder = StringBuilder()
        builder.append(CharArray(DavException.MAX_EXCERPT_SIZE - 1) { '*' })
        builder.append("\u03C0")    // Pi
        val body = builder.toString()

        httpClient.changeMockHandler { request ->
            if (request.url == sampleUrl) {
                respond(
                    "",
                    HttpStatusCode.NotFound,
                    headersOf(HttpHeaders.ContentType, ContentType.Text.Html.toString())
                )
            } else {
                respondError(HttpStatusCode.BadRequest)
            }
        }

        try {
            dav.propfind(0, ResourceType.NAME) { _, _ -> }
            fail("Expected HttpException")
        } catch (e: HttpException) {
            assertEquals(e.code, 404)
            assertTrue(e.errors.isEmpty())
            assertEquals(
                body.substring(0, DavException.MAX_EXCERPT_SIZE - 1),
                e.responseBody!!.substring(0, DavException.MAX_EXCERPT_SIZE - 1)
            )
        }
    }

    test("testResponseNonTextError") {
        val url = sampleUrl
        val dav = DavResource(httpClient, url)

        httpClient.changeMockHandler { request ->
            if (request.url == sampleUrl) {
                respond(
                    "12345",
                    HttpStatusCode.Forbidden,
                    headersOf(HttpHeaders.ContentType, ContentType.Application.OctetStream.toString())
                )
            } else {
                respondError(HttpStatusCode.BadRequest)
            }
        }
        try {
            dav.propfind(0, ResourceType.NAME) { _, _ -> }
            fail("Expected HttpException")
        } catch (e: HttpException) {
            assertEquals(e.code, 403)
            assertTrue(e.errors.isEmpty())
            assertNull(e.responseBody)
        }
    }

    test("testSerialization") {
        val url = sampleUrl
        val dav = DavResource(httpClient, url)

        httpClient.changeMockHandler { request ->
            if (request.url == sampleUrl) {
                respond(
                    "12345",
                    HttpStatusCode.InternalServerError,
                    headersOf(HttpHeaders.ContentType, ContentType.Text.Plain.toString())
                )
            } else {
                respondError(HttpStatusCode.BadRequest)
            }
        }
        try {
            dav.propfind(0, ResourceType.NAME) { _, _ -> }
            fail("Expected DavException")
        } catch (e: DavException) {
            val response = httpClient.lastMockResponse

            assertEquals(HttpStatusCode.InternalServerError, response.statusCode)
            assertTrue(response.body is String)
            assertTrue(response.body.toString().contains("12345"))
        }
    }

    /**
     * Test precondition XML element (sample from RFC 4918 16)
     */
    test("testXmlError") {
        val url = sampleUrl
        val dav = DavResource(httpClient, url)

        val body = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
                "<D:error xmlns:D=\"DAV:\">\n" +
                "  <D:lock-token-submitted>\n" +
                "    <D:href>/workspace/webdav/</D:href>\n" +
                "  </D:lock-token-submitted>\n" +
                "</D:error>\n"
        httpClient.changeMockHandler { request ->
            if (request.url == sampleUrl) {
                respond(
                    body,
                    HttpStatusCode.Locked,
                    headersOf(
                        HttpHeaders.ContentType,
                        ContentType.Application.Xml.withCharset(Charsets.UTF_8).toString()
                    )
                )
            } else {
                respondError(HttpStatusCode.BadRequest)
            }
        }
        try {
            dav.propfind(0, ResourceType.NAME) { _, _ -> }
            fail("Expected HttpException")
        } catch (e: HttpException) {
            assertEquals(e.code, 423)
            assertTrue(e.errors.any { it.name == QName(XmlUtils.NS_WEBDAV, "lock-token-submitted") })
            assertEquals(body, e.responseBody)
        }
    }

})