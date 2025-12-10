/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

package at.bitfire.dav4jvm.ktor

import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.ktor.exception.DavException
import at.bitfire.dav4jvm.ktor.exception.HttpException
import at.bitfire.dav4jvm.property.webdav.DisplayName
import at.bitfire.dav4jvm.property.webdav.GetETag
import at.bitfire.dav4jvm.property.webdav.ResourceType
import at.bitfire.dav4jvm.property.webdav.WebDAV
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.engine.mock.respondOk
import io.ktor.client.engine.mock.respondRedirect
import io.ktor.client.request.get
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.ContentType
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.content.TextContent
import io.ktor.http.contentType
import io.ktor.http.fullPath
import io.ktor.http.headersOf
import io.ktor.http.takeFrom
import io.ktor.http.withCharset
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DavResourceTest {

    private val sampleText = "SAMPLE RESPONSE"
    private val sampleUrl = Url("https://127.0.0.1/dav/")
    private val sampleDestination = URLBuilder(sampleUrl).takeFrom("test").build()

    // Helper methods for common test patterns
    private fun createMockEngineForCopy(status: HttpStatusCode): MockEngine {
        return MockEngine {
            respond(
                content = sampleText,
                status = status,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Plain.toString())
            )
        }
    }

    private fun createMockEngineForDelete(status: HttpStatusCode): MockEngine {
        return MockEngine {
            respond(sampleText, status)
        }
    }

    private fun createMockEngineForGet(status: HttpStatusCode, eTag: String? = null, content: String = sampleText): MockEngine {
        return MockEngine {
            respond(
                content = content,
                status = status,
                headers = HeadersBuilder().apply {
                    eTag?.let { append(HttpHeaders.ETag, it) }
                    append(HttpHeaders.ContentType, "application/x-test-result")
                }.build()
            )
        }
    }

    private fun createMockEngineForPut(status: HttpStatusCode, eTag: String? = null): MockEngine {
        return MockEngine {
            respond(
                content = " ",
                status = status,
                headers = headersOf(HttpHeaders.ETag, eTag ?: "W/\"Weak PUT ETag\"")
            )
        }
    }

    private fun createMockEngineForPropfind(xmlResponse: String): MockEngine {
        return MockEngine {
            respond(
                content = xmlResponse,
                status = HttpStatusCode.MultiStatus,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Xml.withCharset(Charsets.UTF_8).toString())
            )
        }
    }


    @Test
    fun `Copy POSITIVE no preconditions, 201 Created, resulted in the creation of a new resource`() = runTest {
        val mockEngine = createMockEngineForCopy(HttpStatusCode.Created)
        val httpClient = HttpClient(mockEngine)
        var called = false

        DavResource(httpClient, sampleUrl).let { dav ->
            dav.copy(sampleDestination, false) {
                called = true
            }
            assertTrue(called)
        }

        val rq = mockEngine.requestHistory.last()
        assertEquals(HttpMethod.parse("COPY"), rq.method)
        assertEquals(sampleUrl.encodedPath, rq.url.encodedPath)
        assertEquals(sampleDestination.toString(), rq.headers[HttpHeaders.Destination])
        assertEquals("F", rq.headers[HttpHeaders.Overwrite])
    }

    @Test
    fun `Copy POSITIVE no preconditions, 204 No content, resource successfully copied to a preexisting destination resource`() = runTest {
        val mockEngine = createMockEngineForCopy(HttpStatusCode.NoContent)
        val httpClient = HttpClient(mockEngine)

        var called = false
        DavResource(httpClient, sampleUrl).let { dav ->
            dav.copy(sampleDestination, true) {
                called = true
            }
            assertTrue(called)
        }

        val rq = mockEngine.requestHistory.last()
        assertEquals(HttpMethod.parse("COPY"), rq.method)
        assertEquals(sampleUrl.encodedPath, rq.url.encodedPath)
        assertEquals(sampleDestination.toString(), rq.headers[HttpHeaders.Destination])
        assertNull(rq.headers[HttpHeaders.Overwrite])
    }

    @Test
    fun `Copy NEGATIVE 207 multi-status eg errors on some of resources affected by the COPY prevented the operation from taking place`() = runTest {
        val mockEngine = createMockEngineForCopy(HttpStatusCode.MultiStatus)
        val httpClient = HttpClient(mockEngine)
        var called = false

        try {
            called = false
            DavResource(httpClient, sampleUrl).let { dav ->
                dav.copy(sampleDestination, false) { called = true }
                fail("Expected HttpException")
            }
        } catch (_: HttpException) {
            assertFalse(called)
        }
    }

    @Test
    fun `Delete only eTag POSITIVE TEST CASES  precondition If-Match 200 OK`() = runTest {
        val mockEngine = createMockEngineForDelete(HttpStatusCode.NoContent)
        val httpClient = HttpClient(mockEngine)
        val dav = DavResource(httpClient, sampleUrl)
        var called = false

        dav.delete { called = true }
        assertTrue(called)
        val rq = mockEngine.requestHistory.last()
        assertEquals(HttpMethod.Delete, rq.method)
        assertEquals(sampleUrl.encodedPath, rq.url.encodedPath)
        assertNull(rq.headers[HttpHeaders.IfMatch])
    }

    @Test
    fun `Delete eTag and schedule Tag POSITIVE TEST CASES  precondition If-Match 200 OK`() = runTest {
        val mockEngine = MockEngine { respondOk(content = sampleText) }
        val httpClient = HttpClient(mockEngine)
        val dav = DavResource(httpClient, sampleUrl)
        var called = false

        dav.delete(headersOf("If-Match", "\"SomeETag\"")) { called = true }
        assertTrue(called)

        val rq = mockEngine.requestHistory.last()
        assertEquals("\"SomeETag\"", rq.headers[HttpHeaders.IfMatch])
    }

    @Test
    fun `Delete POSITIVE TEST CASES  precondition If-Match 302 Moved Temporarily`() = runTest {
        var numResponses = 0
        val mockEngine = MockEngine {
            numResponses += 1
            when (numResponses) {
                1 -> respondRedirect("/new-location")
                else -> respondOk()
            }
        }
        val httpClient = HttpClient(mockEngine)
        val dav = DavResource(httpClient, sampleUrl)
        var called = false

        dav.delete(null) { called = true }
        assertTrue(called)
    }

    @Test
    fun `Delete NEGATIVE TEST CASES precondition If-Match 207 multi-status`() = runTest {
        val mockEngine = MockEngine { respondError(HttpStatusCode.MultiStatus) }
        val httpClient = HttpClient(mockEngine)
        val dav = DavResource(httpClient, sampleUrl)
        var called = false

        try {
            dav.delete(null) { called = true }
            fail("Expected HttpException")
        } catch (_: HttpException) {
            assertFalse(called)
        }
    }


    @Test
    fun `followRedirects 302 Found`() = runTest {
        var numResponses = 0
        val mockEngine = MockEngine {
            numResponses += 1
            when (numResponses) {
                1 -> respond("New location!", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "https://to.com/"))
                else -> respond("", HttpStatusCode.NoContent, headersOf(HttpHeaders.Location, "https://to.com/"))
            }
        }
        val httpClient = HttpClient(mockEngine) {
            followRedirects = false
        }
        val dav = DavResource(httpClient, sampleUrl)

        dav.followRedirects({
            httpClient.prepareGet("https://from.com/")
        }) { response ->
            assertEquals(HttpStatusCode.NoContent, response.status)
            assertEquals(Url("https://to.com/"), dav.location)
        }
    }

    @Test(expected = DavException::class)
    fun `followRedirects Https To Http`() = runTest {
        val mockEngine = MockEngine {
            respond("New location!", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "http://to.com/"))
        }
        val httpClient = HttpClient(mockEngine) {
            followRedirects = false
        }

        val dav = DavResource(httpClient, Url("https://from.com"))
        dav.followRedirects({
            httpClient.prepareGet("https://from.com/")
        }) {}
    }

    @Test
    fun `Get POSITIVE TEST CASES 200 OK`() = runTest {
        val mockEngine = createMockEngineForGet(status = HttpStatusCode.OK, eTag = "W/\"My Weak ETag\"")
        val httpClient = HttpClient(mockEngine)
        val dav = DavResource(httpClient, sampleUrl)
        var called = false

        dav.get { response ->
            called = true
            assertEquals(sampleText, response.bodyAsText())

            val eTag = GetETag.fromHttpResponse(response)
            assertEquals("My Weak ETag", eTag!!.eTag)
            assertTrue(eTag.weak)
            assertEquals(ContentType.parse("application/x-test-result"), response.contentType())
        }
        assertTrue(called)

        val rq = mockEngine.requestHistory.last()
        assertEquals(HttpMethod.Get, rq.method)
        assertEquals(sampleUrl.encodedPath, rq.url.encodedPath)
        assertEquals(ContentType.Any.toString(), rq.headers[HttpHeaders.Accept])
    }

    @Test
    fun `Get POSITIVE TEST CASES 302 Moved Temporarily + 200 OK`() = runTest {
        var numResponses = 0
        val mockEngine = MockEngine {
            numResponses += 1
            when (numResponses) {
                1 -> respond("This resource was moved.", HttpStatusCode.TemporaryRedirect, headersOf(HttpHeaders.Location, "/target"))
                else -> respond(sampleText, HttpStatusCode.OK, headersOf(HttpHeaders.ETag, "\"StrongETag\""))
            }
        }
        val httpClient = HttpClient(mockEngine)
        val dav = DavResource(httpClient, sampleUrl)
        var called = false

        dav.get { response ->
            called = true
            assertEquals(sampleText, response.bodyAsText())
            val eTag = GetETag(response.headers[HttpHeaders.ETag])
            assertEquals("StrongETag", eTag.eTag)
            assertFalse(eTag.weak)
        }
        assertTrue(called)

        val rq = mockEngine.requestHistory.last()
        assertEquals(HttpMethod.Get, rq.method)
        assertEquals("/target", rq.url.fullPath)
    }

    @Test
    fun `Get POSITIVE TEST CASES 200 OK without ETag in response`() = runTest {
        val mockEngine = createMockEngineForGet(status = HttpStatusCode.OK, eTag = null)
        val httpClient = HttpClient(mockEngine)
        val dav = DavResource(httpClient, sampleUrl)
        var called = false

        dav.get { response ->
            called = true
            assertNull(response.headers[HttpHeaders.ETag])
        }
        assertTrue(called)
    }


    @Test
    fun `GetRange Ok`() = runTest {
        val mockEngine = MockEngine {
            respond("", HttpStatusCode.PartialContent)     // 206
        }
        val httpClient = HttpClient(mockEngine)
        val dav = DavResource(httpClient, sampleUrl)

        var called = false
        dav.getRange(100, 342) { response ->
            assertEquals("bytes=100-441", response.request.headers[HttpHeaders.Range])
            called = true
        }
        assertTrue(called)
    }

    @Test
    fun `Post POSITIVE TEST CASES 200 OK`() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = sampleText,
                status = HttpStatusCode.OK,     // 200 OK
                headers = HeadersBuilder().apply {
                    append(HttpHeaders.ContentType, "application/x-test-result")
                    append(HttpHeaders.ETag, "W/\"My Weak ETag\"")
                }.build()
            )
        }
        val httpClient = HttpClient(mockEngine)
        val dav = DavResource(httpClient, sampleUrl)

        /* POSITIVE TEST CASES */

        // 200 OK
        var called = false
        dav.post(
            provideBody = { ByteReadChannel("body") },
            mimeType = ContentType.parse("application/x-test-result")
        ) { response ->
            called = true
            assertEquals(sampleText, response.bodyAsText())

            val eTag = GetETag.fromHttpResponse(response)
            assertEquals("My Weak ETag", eTag!!.eTag)
            assertTrue(eTag.weak)
            assertEquals(ContentType.parse("application/x-test-result"), response.contentType())
        }
        assertTrue(called)

        val rq = mockEngine.requestHistory.last()
        assertEquals(HttpMethod.Post, rq.method)
        assertEquals(sampleUrl.encodedPath, rq.url.encodedPath)
        assertEquals(
            ContentType.parse("application/x-test-result"),
            rq.body.contentType
        )  // TODO: Originally there was a check for the header, not the content type in the body, what is correct here?
        //assertEquals("body", (rq.body as TextContent).text)

        /*
        // 302 Moved Temporarily + 200 OK
        called = false
        dav.post(
            body = "body",
            headers = HeadersBuilder().apply { append(HttpHeaders.ContentType, ContentType.parse("application/x-test-result").toString()) }.build()
        ) { response ->
            called = true
            assertEquals(sampleText, response.bodyAsText())
            val eTag = GetETag(response.headers[HttpHeaders.ETag])
            assertEquals("StrongETag", eTag.eTag)
            assertFalse(eTag.weak)
        }
        assertTrue(called)

        rq = mockEngine.requestHistory.last()
        assertEquals(HttpMethod.Post, rq.method)
        assertEquals("/target", rq.url.encodedPath)

        // 200 OK without ETag in response
        called = false
        dav.post(
            body = "body",
        ) { response ->
            called = true
            assertNull(response.headers[HttpHeaders.ETag])
        }
        assertTrue(called)

         */
    }

    @Test
    fun `Post POSITIVE TEST CASES 302 Moved Temporarily + 200 OK`() = runTest {
        var numResponses = 0
        val mockEngine = MockEngine {
            numResponses += 1
            when (numResponses) {
                1 -> respond("This resource was moved.", HttpStatusCode.TemporaryRedirect, headersOf(HttpHeaders.Location, "/target"))
                else -> respond(sampleText, HttpStatusCode.OK, headersOf(HttpHeaders.ETag, "\"StrongETag\""))
            }
        }
        val httpClient = HttpClient(mockEngine)
        val dav = DavResource(httpClient, sampleUrl)

        var called = false
        dav.post(
            provideBody = { ByteReadChannel("body") },
            mimeType = ContentType.parse("application/x-test-result")
        ) { response ->
            called = true
            assertEquals(sampleText, response.bodyAsText())
            val eTag = GetETag(response.headers[HttpHeaders.ETag])
            assertEquals("StrongETag", eTag.eTag)
            assertFalse(eTag.weak)
        }
        assertTrue(called)

        val rq = mockEngine.requestHistory.last()
        assertEquals(HttpMethod.Post, rq.method)
        assertEquals("/target", rq.url.encodedPath)
    }

    @Test
    fun `Post POSITIVE TEST CASES 200 OK without ETag in response`() = runTest {
        val mockEngine = MockEngine {
            respond(sampleText, HttpStatusCode.OK)
        }
        val httpClient = HttpClient(mockEngine)
        val dav = DavResource(httpClient, sampleUrl)

        var called = false
        dav.post(
            provideBody = { ByteReadChannel("body") },
            mimeType = ContentType.Text.Plain
        ) { response ->
            called = true
            assertNull(response.headers[HttpHeaders.ETag])
        }
        assertTrue(called)
    }

    @Test
    fun `Move POSITIVE TEST CASES no preconditions, 201 Created, new URL mapping at the destination`() = runTest {
        val mockEngine = MockEngine {
            respond("", HttpStatusCode.Created)     // 201 Created
        }
        val httpClient = HttpClient(mockEngine)
        val destination = URLBuilder(sampleUrl).takeFrom("test").build()

        // no preconditions, 201 Created, new URL mapping at the destination
        var called = false
        DavResource(httpClient, sampleUrl).let { dav ->
            dav.move(destination, false) {
                called = true
            }
            assertTrue(called)
            assertEquals(destination, dav.location)
        }

        val rq = mockEngine.requestHistory.last()
        assertEquals(HttpMethod.parse("MOVE"), rq.method)
        assertEquals(sampleUrl.encodedPath, rq.url.encodedPath)
        assertEquals(destination.toString(), rq.headers[HttpHeaders.Destination])
        assertEquals("F", rq.headers[HttpHeaders.Overwrite])
    }

    @Test
    fun `Move POSITIVE TEST CASES no preconditions, 204 No content, URL already mapped, overwrite`() = runTest {
        val mockEngine = MockEngine {
            respond("", HttpStatusCode.NoContent)     // 204 No content
        }
        val httpClient = HttpClient(mockEngine)
        val destination = URLBuilder(sampleUrl).takeFrom("test").build()

        var called = false
        DavResource(httpClient, sampleUrl).let { dav ->
            dav.move(destination, true) {
                called = true
            }
            assertTrue(called)
            assertEquals(destination, dav.location)
        }

        val rq = mockEngine.requestHistory.last()
        assertEquals(HttpMethod.parse("MOVE"), rq.method)
        assertEquals(sampleUrl.encodedPath, rq.url.encodedPath)
        assertEquals(destination.toString(), rq.headers[HttpHeaders.Destination])
        assertNull(rq.headers[HttpHeaders.Overwrite])
    }

    @Test
    fun `Move NEGATIVE TEST CASES no preconditions, 207 multi-status`() = runTest {
        val mockEngine = MockEngine {
            respond("", HttpStatusCode.MultiStatus)     // 207 Multi-Status
        }
        val httpClient = HttpClient(mockEngine)
        val destination = URLBuilder(sampleUrl).takeFrom("test").build()

        // 207 multi-status (e.g. errors on some of resources affected by
        // the MOVE prevented the operation from taking place)
        var called = false
        try {
            DavResource(httpClient, sampleUrl).let { dav ->
                dav.move(destination, false) { called = true }
                fail("Expected HttpException")
            }
        } catch (_: HttpException) {
            assertFalse(called)
        }
    }

    @Test
    fun `Options with capabilities`() = runTest {
        val mockEngine = MockEngine {
            respond("", HttpStatusCode.OK, HeadersBuilder().apply { append("DAV", "  1,  2 ,3,hyperactive-access") }.build())     // 200 Ok
        }
        val httpClient = HttpClient(mockEngine)
        val dav = DavResource(httpClient, sampleUrl)

        var called = false
        dav.options { davCapabilities, _ ->
            called = true
            assertTrue(davCapabilities.any { it.contains("1") })
            assertTrue(davCapabilities.any { it.contains("2") })
            assertTrue(davCapabilities.any { it.contains("3") })
            assertTrue(davCapabilities.any { it.contains("hyperactive-access") })
        }
        assertTrue(called)
    }


    @Test
    fun `Options without capabilities`() = runTest {
        val mockEngine = MockEngine {
            respondOk()     // 200 OK
        }
        val httpClient = HttpClient(mockEngine)
        val dav = DavResource(httpClient, sampleUrl)

        var called = false
        dav.options { davCapabilities, _ ->
            called = true
            assertTrue(davCapabilities.isEmpty())
        }
        assertTrue(called)
    }

    @Test
    fun `NEGATIVE TEST CASES Propfind And MultiStatus 500 Internal Server Error`() = runTest {
        val mockEngine = MockEngine {
            respondError(HttpStatusCode.InternalServerError)     // 500
        }
        val httpClient = HttpClient(mockEngine)
        val dav = DavResource(httpClient, sampleUrl)

        var called = false
        try {
            dav.propfind(0, WebDAV.ResourceType) { _, _ -> called = true }
            fail("Expected HttpException")
        } catch (_: HttpException) {
            assertFalse(called)
        }
    }

    @Test
    fun `NEGATIVE TEST CASES Propfind And MultiStatus 200 OK (instead of 207 Multi-Status)`() = runTest {
        val mockEngine = MockEngine { respondOk() }
        val httpClient = HttpClient(mockEngine)
        val dav = DavResource(httpClient, sampleUrl)

        // * 200 OK (instead of 207 Multi-Status)
        var called = false
        try {
            dav.propfind(0, WebDAV.ResourceType) { _, _ -> called = true }
            fail("Expected DavException")
        } catch (_: DavException) {
            assertFalse(called)
        }
    }

    @Test
    fun `NEGATIVE TEST CASES Propfind And MultiStatus non-XML response`() = runTest {
        val mockEngine = MockEngine {
            respond("<html></html>", HttpStatusCode.MultiStatus, headersOf(HttpHeaders.ContentType, ContentType.Text.Html.toString()))   // non-XML response
        }
        val httpClient = HttpClient(mockEngine)
        val dav = DavResource(httpClient, sampleUrl)

        // * non-XML response
        var called = false
        try {
            dav.propfind(0, WebDAV.ResourceType) { _, _ -> called = true }
            fail("Expected DavException")
        } catch (_: DavException) {
            assertFalse(called)
        }
    }

    @Test
    fun `NEGATIVE TEST CASES Propfind And MultiStatus malformed XML response`() = runTest {
        val mockEngine = MockEngine {
            respond(
                "<malformed-xml>",
                HttpStatusCode.MultiStatus,
                headersOf(HttpHeaders.ContentType, ContentType.Application.Xml.withCharset(Charsets.UTF_8).toString())
            )   // * malformed XML response
        }
        val httpClient = HttpClient(mockEngine)
        val dav = DavResource(httpClient, sampleUrl)

        // * malformed XML response
        var called = false
        try {
            dav.propfind(0, WebDAV.ResourceType) { _, _ -> called = true }
            fail("Expected DavException")
        } catch (_: DavException) {
            assertFalse(called)
        }
    }


    @Test
    fun `NEGATIVE TEST CASES Propfind And MultiStatus response without multistatus root element`() = runTest {
        val mockEngine = createMockEngineForPropfind("<test></test>")
        val httpClient = HttpClient(mockEngine)
        val dav = DavResource(httpClient, sampleUrl)

        var called = false
        try {
            dav.propfind(0, WebDAV.ResourceType) { _, _ -> called = true }
            fail("Expected DavException")
        } catch (_: DavException) {
            assertFalse(called)
        }
    }

    @Test
    fun `NEGATIVE TEST CASES Propfind And MultiStatus multi-status response with invalid status in response`() = runTest {
        val xmlResponse = "<multistatus xmlns='DAV:'>" +
                "  <response>" +
                "    <href>/dav</href>" +
                "    <status>Invalid Status Line</status>" +
                "  </response>" +
                "</multistatus>"
        val mockEngine = createMockEngineForPropfind(xmlResponse)
        val httpClient = HttpClient(mockEngine)
        val dav = DavResource(httpClient, sampleUrl)

        var called = false
        dav.propfind(0, WebDAV.ResourceType) { response, relation ->
            assertEquals(Response.HrefRelation.SELF, relation)
            assertEquals(HttpStatusCode.InternalServerError, response.status)
            called = true
        }
        assertTrue(called)
    }

    @Test
    fun `NEGATIVE TEST CASES Propfind And MultiStatus multi-status response with response-status element indicating failure`() = runTest {
        val xmlResponse = "<multistatus xmlns='DAV:'>" +
                "  <response>" +
                "    <href>/dav</href>" +
                "    <status>HTTP/1.1 403 Forbidden</status>" +
                "  </response>" +
                "</multistatus>"
        val mockEngine = createMockEngineForPropfind(xmlResponse)
        val httpClient = HttpClient(mockEngine)
        val dav = DavResource(httpClient, sampleUrl)

        var called = false
        dav.propfind(0, WebDAV.ResourceType) { response, relation ->
            assertEquals(Response.HrefRelation.SELF, relation)
            assertEquals(HttpStatusCode.Forbidden, response.status)
            called = true
        }
        assertTrue(called)
    }

    @Test
    fun `NEGATIVE TEST CASES Propfind And MultiStatus multi-status response with invalid status in propstat`() = runTest {
        val xmlResponse = "<multistatus xmlns='DAV:'>" +
                "  <response>" +
                "    <href>/dav</href>" +
                "    <propstat>" +
                "      <prop>" +
                "        <resourcetype><collection/></resourcetype>" +
                "      </prop>" +
                "      <status>Invalid Status Line</status>" +
                "    </propstat>" +
                "  </response>" +
                "</multistatus>"
        val mockEngine = createMockEngineForPropfind(xmlResponse)
        val httpClient = HttpClient(mockEngine)
        val dav = DavResource(httpClient, sampleUrl)

        var called = false
        dav.propfind(0, WebDAV.ResourceType) { response, relation ->
            called = true
            assertEquals(Response.HrefRelation.SELF, relation)
            assertTrue(response.properties.filterIsInstance<ResourceType>().isEmpty())
        }
        assertTrue(called)
    }

    @Test
    fun `NEGATIVE TEST CASES Propfind And MultiStatus multi-status response without response elements`() = runTest {
        val mockEngine = createMockEngineForPropfind("<multistatus xmlns='DAV:'></multistatus>")
        val httpClient = HttpClient(mockEngine)
        val dav = DavResource(httpClient, sampleUrl)

        dav.propfind(0, WebDAV.ResourceType) { _, _ ->
            fail("Shouldn't be called")
        }
    }

    @Test
    fun `POSITIVE TEST CASES Propfind And MultiStatus multi-status response with response-status element indicating success`() = runTest {
        val mockEngine = MockEngine {
            respond(
                "<multistatus xmlns='DAV:'>" +
                        "  <response>" +
                        "    <href>/dav</href>" +
                        "    <status>HTTP/1.1 200 OK</status>" +
                        "  </response>" +
                        "</multistatus>",
                HttpStatusCode.MultiStatus,
                headersOf(HttpHeaders.ContentType, ContentType.Application.Xml.withCharset(Charsets.UTF_8).toString())
            )   // multi-status response with <response>/<status> element indicating success
        }
        val httpClient = HttpClient(mockEngine)
        val dav = DavResource(httpClient, sampleUrl)

        var called = false
        // multi-status response with <response>/<status> element indicating success
        dav.propfind(0, WebDAV.ResourceType) { response, relation ->
            called = true
            assertTrue(response.isSuccess())
            assertEquals(Response.HrefRelation.SELF, relation)
            assertEquals(0, response.properties.size)
        }
        assertTrue(called)
    }


    @Test
    fun `POSITIVE TEST CASES Propfind And MultiStatus multi-status response with response-propstat element`() = runTest {
        val mockEngine = MockEngine {
            respond(
                "<multistatus xmlns='DAV:'>" +
                        "  <response>" +
                        "    <href>/dav</href>" +
                        "    <propstat>" +
                        "      <prop>" +
                        "         <resourcetype></resourcetype>" +
                        "        <displayname>My DAV Collection</displayname>" +
                        "      </prop>" +
                        "      <status>HTTP/1.1 200 OK</status>" +
                        "    </propstat>" +
                        "  </response>" +
                        "</multistatus>",
                HttpStatusCode.MultiStatus,
                headersOf(HttpHeaders.ContentType, ContentType.Application.Xml.withCharset(Charsets.UTF_8).toString())
            )   // multi-status response with <response>/<propstat> element
        }
        val httpClient = HttpClient(mockEngine)
        val dav = DavResource(httpClient, sampleUrl)

        var called = false
        dav.propfind(0, WebDAV.ResourceType, WebDAV.DisplayName) { response, relation ->
            called = true
            assertTrue(response.isSuccess())
            assertEquals(Response.HrefRelation.SELF, relation)
            assertEquals("My DAV Collection", response[DisplayName::class.java]?.displayName)
        }
        assertTrue(called)
    }

    @Test
    fun `POSITIVE TEST CASES Propfind And MultiStatus SPECIAL CASES multi-status response for collection with several members, incomplete (not all resourcetypes listed)`() =
        runTest {
            val mockEngine = MockEngine {
                respond(
                    "<multistatus xmlns='DAV:'>" +
                            "  <response>" +
                            "    <href>" + sampleUrl.toString() + "</href>" +
                            "    <propstat>" +
                            "      <prop>" +
                            "        <resourcetype><collection/></resourcetype>" +
                            "        <displayname>My DAV Collection</displayname>" +
                            "      </prop>" +
                            "      <status>HTTP/1.1 200 OK</status>" +
                            "    </propstat>" +
                            "  </response>" +
                            "  <response>" +
                            "    <href>/dav/subcollection</href>" +
                            "    <propstat>" +
                            "      <prop>" +
                            "        <resourcetype><collection/></resourcetype>" +
                            "        <displayname>A Subfolder</displayname>" +
                            "      </prop>" +
                            "      <status>HTTP/1.1 200 OK</status>" +
                            "    </propstat>" +
                            "  </response>" +
                            "  <response>" +
                            "    <href>/dav/uid@host:file</href>" +
                            "    <propstat>" +
                            "      <prop>" +
                            "        <displayname>Absolute path with @ and :</displayname>" +
                            "      </prop>" +
                            "      <status>HTTP/1.1 200 OK</status>" +
                            "    </propstat>" +
                            "  </response>" +
                            "  <response>" +
                            "    <href>relative-uid@host.file</href>" +
                            "    <propstat>" +
                            "      <prop>" +
                            "        <displayname>Relative path with @</displayname>" +
                            "      </prop>" +
                            "      <status>HTTP/1.1 200 OK</status>" +
                            "    </propstat>" +
                            "  </response>" +
                            "  <response>" +
                            "    <href>relative:colon.vcf</href>" +
                            "    <propstat>" +
                            "      <prop>" +
                            "        <displayname>Relative path with colon</displayname>" +
                            "      </prop>" +
                            "      <status>HTTP/1.1 200 OK</status>" +
                            "    </propstat>" +
                            "  </response>" +
                            "  <response>" +
                            "    <href>/something-very/else</href>" +
                            "    <propstat>" +
                            "      <prop>" +
                            "        <displayname>Not requested</displayname>" +
                            "      </prop>" +
                            "      <status>HTTP/1.1 200 OK</status>" +
                            "    </propstat>" +
                            "  </response>" +
                            "</multistatus>",
                    HttpStatusCode.MultiStatus,
                    headersOf(HttpHeaders.ContentType, ContentType.Application.Xml.withCharset(Charsets.UTF_8).toString())
                )   // multi-status response for collection with several members; incomplete (not all <resourcetype>s listed)
            }
            val httpClient = HttpClient(mockEngine)
            val dav = DavResource(httpClient, sampleUrl)

            // multi-status response for collection with several members; incomplete (not all <resourcetype>s listed)
            var nrCalled = 0
            dav.propfind(1, WebDAV.ResourceType, WebDAV.DisplayName) { response, relation ->
                when (response.href) {
                    URLBuilder(sampleUrl).takeFrom("/dav/").build() -> {
                        assertTrue(response.isSuccess())
                        assertEquals(Response.HrefRelation.SELF, relation)
                        assertTrue(response[ResourceType::class.java]!!.types.contains(WebDAV.Collection))
                        assertEquals("My DAV Collection", response[DisplayName::class.java]?.displayName)
                        nrCalled++
                    }

                    URLBuilder(sampleUrl).takeFrom("/dav/subcollection/").build() -> {
                        assertTrue(response.isSuccess())
                        assertEquals(Response.HrefRelation.MEMBER, relation)
                        assertTrue(response[ResourceType::class.java]!!.types.contains(WebDAV.Collection))
                        assertEquals("A Subfolder", response[DisplayName::class.java]?.displayName)
                        nrCalled++
                    }

                    URLBuilder(sampleUrl).takeFrom("/dav/uid@host:file").build() -> {
                        assertTrue(response.isSuccess())
                        assertEquals(Response.HrefRelation.MEMBER, relation)
                        assertEquals("Absolute path with @ and :", response[DisplayName::class.java]?.displayName)
                        nrCalled++
                    }

                    URLBuilder(sampleUrl).takeFrom("/dav/relative-uid@host.file").build() -> {
                        assertTrue(response.isSuccess())
                        assertEquals(Response.HrefRelation.MEMBER, relation)
                        assertEquals("Relative path with @", response[DisplayName::class.java]?.displayName)
                        nrCalled++
                    }

                    URLBuilder(sampleUrl).takeFrom("/dav/relative:colon.vcf").build() -> {
                        assertTrue(response.isSuccess())
                        assertEquals(Response.HrefRelation.MEMBER, relation)
                        assertEquals("Relative path with colon", response[DisplayName::class.java]?.displayName)
                        nrCalled++
                    }
                }
            }
            assertEquals(5, nrCalled)
        }

    @Test
    fun `POSITIVE TEST CASES Propfind And MultiStatus SPECIAL CASES same property is sent as 200 OK and 404 Not Found in same response (seen in iCloud)`() = runTest {
        val mockEngine = MockEngine {
            respond(
                "<multistatus xmlns='DAV:'>" +
                        "  <response>" +
                        "    <href>" + sampleUrl.toString() + "</href>" +
                        "    <propstat>" +
                        "      <prop>" +
                        "        <resourcetype><collection/></resourcetype>" +
                        "        <displayname>My DAV Collection</displayname>" +
                        "      </prop>" +
                        "      <status>HTTP/1.1 200 OK</status>" +
                        "    </propstat>" +
                        "    <propstat>" +
                        "      <prop>" +
                        "        <resourcetype/>" +
                        "      </prop>" +
                        "      <status>HTTP/1.1 404 Not Found</status>" +
                        "    </propstat>" +
                        "  </response>" +
                        "</multistatus>",
                HttpStatusCode.MultiStatus,
                headersOf(HttpHeaders.ContentType, ContentType.Application.Xml.withCharset(Charsets.UTF_8).toString())
            )   // same property is sent as 200 OK and 404 Not Found in same <response> (seen in iCloud)
        }
        val httpClient = HttpClient(mockEngine)
        val dav = DavResource(httpClient, sampleUrl)

        var called = false
        dav.propfind(0, WebDAV.ResourceType, WebDAV.DisplayName) { response, relation ->
            called = true
            assertTrue(response.isSuccess())
            assertEquals(Response.HrefRelation.SELF, relation)
            assertEquals(URLBuilder(sampleUrl).takeFrom("/dav/").build(), response.href)
            assertTrue(response[ResourceType::class.java]!!.types.contains(WebDAV.Collection))
            assertEquals("My DAV Collection", response[DisplayName::class.java]?.displayName)
        }
        assertTrue(called)
    }


    @Test
    fun `POSITIVE TEST CASES Propfind And MultiStatus SPECIAL CASES multi-status response with propstat that doesn't contain status, assume 200 OK`() = runTest {
        val mockEngine = MockEngine {
            respond(
                "<multistatus xmlns='DAV:'>" +
                        "  <response>" +
                        "    <href>/dav</href>" +
                        "    <propstat>" +
                        "      <prop>" +
                        "        <displayname>Without Status</displayname>" +
                        "      </prop>" +
                        "    </propstat>" +
                        "  </response>" +
                        "</multistatus>",
                HttpStatusCode.MultiStatus,
                headersOf(HttpHeaders.ContentType, ContentType.Application.Xml.withCharset(Charsets.UTF_8).toString())
            )   // multi-status response with <propstat> that doesn't contain <status> (=> assume 200 OK)
        }
        val httpClient = HttpClient(mockEngine)
        val dav = DavResource(httpClient, sampleUrl)

        /*** SPECIAL CASES ***/

        // multi-status response with <propstat> that doesn't contain <status> (=> assume 200 OK)
        var called = false
        dav.propfind(0, WebDAV.DisplayName) { response, _ ->
            called = true
            assertEquals(200, response.propstat.first().status.value)
            assertEquals("Without Status", response[DisplayName::class.java]?.displayName)
        }
        assertTrue(called)
    }

    @Test
    fun proppatch() = runTest {        // multi-status response with <response>/<propstat> elements
        val mockEngine = MockEngine {
            respond(
                content = "<multistatus xmlns='DAV:' xmlns:s='sample'>" +
                        "  <response>" +
                        "    <href>/dav</href>" +
                        "    <propstat>" +
                        "      <prop>" +
                        "         <s:setThis>Some Value</s:setThis>" +
                        "      </prop>" +
                        "      <status>HTTP/1.1 200 OK</status>" +
                        "    </propstat>" +
                        "    <propstat>" +
                        "      <prop>" +
                        "         <s:removeThis/>" +
                        "      </prop>" +
                        "      <status>HTTP/1.1 404 Not Found</status>" +
                        "    </propstat>" +
                        "  </response>" +
                        "</multistatus>",
                status = HttpStatusCode.MultiStatus,  // 207
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Xml.withCharset(Charsets.UTF_8).toString())
            )
        }
        val httpClient = HttpClient(mockEngine)
        val dav = DavResource(httpClient, sampleUrl)

        var called = false
        dav.proppatch(
            setProperties = mapOf(Pair(Property.Name("sample", "setThis"), "Some Value")),
            removeProperties = listOf(Property.Name("sample", "removeThis"))
        ) { _, hrefRelation ->
            called = true
            assertEquals(Response.HrefRelation.SELF, hrefRelation)
        }
        assertTrue(called)
    }


    @Test
    fun `Proppatch createProppatchXml`() = runTest {
        val xml = DavResource.createProppatchXml(
            setProperties = mapOf(Pair(Property.Name("sample", "setThis"), "Some Value")),
            removeProperties = listOf(Property.Name("sample", "removeThis"))
        )
        assertEquals(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                    "<d:propertyupdate xmlns:d=\"DAV:\">" +
                    "<d:set><d:prop><n1:setThis xmlns:n1=\"sample\">Some Value</n1:setThis></d:prop></d:set>" +
                    "<d:remove><d:prop><n2:removeThis xmlns:n2=\"sample\" /></d:prop></d:remove>" +
                    "</d:propertyupdate>", xml
        )
    }

    @Test
    fun `Put POSITIVE TEST CASES no preconditions, 201 Created`() = runTest {
        val mockEngine = createMockEngineForPut(HttpStatusCode.Created)
        val httpClient = HttpClient(mockEngine)
        val dav = DavResource(httpClient, sampleUrl)

        var called = false
        dav.put(
            provideBody = { ByteReadChannel(sampleText) },
            mimeType = ContentType.Text.Plain
        ) { response ->
            called = true
            val eTag = GetETag.fromHttpResponse(response)!!
            assertEquals("Weak PUT ETag", eTag.eTag)
            assertTrue(eTag.weak)
            assertEquals(response.request.url, dav.location)
        }
        assertTrue(called)

        val rq = mockEngine.requestHistory.last()
        assertEquals(HttpMethod.Put, rq.method)
        assertEquals(sampleUrl.encodedPath, rq.url.encodedPath)
        assertNull(rq.headers[HttpHeaders.IfMatch])
        assertNull(rq.headers[HttpHeaders.IfNoneMatch])
    }

    @Test
    fun `Put POSITIVE TEST CASES precondition  If-None-Match, 301 Moved Permanently + 204 No Content, no ETag in response`() = runTest {
        var numberOfResponse = 0
        val mockEngine = MockEngine {
            numberOfResponse += 1
            when (numberOfResponse) {
                1 -> respond("", HttpStatusCode.MovedPermanently, headersOf(HttpHeaders.Location, "/target"))
                else -> respond("", HttpStatusCode.NoContent)
            }
        }

        val httpClient = HttpClient(mockEngine)
        val dav = DavResource(httpClient, sampleUrl)

        var called = false
        dav.put(
            { ByteReadChannel(sampleText) },
            ContentType.Text.Plain,
            headersOf(HttpHeaders.IfNoneMatch, "*")
        ) { response ->
            called = true
            assertEquals(URLBuilder(sampleUrl).takeFrom("/target").build(), response.request.url)
            val eTag = GetETag.fromHttpResponse(response)
            assertNull("Weak PUT ETag", eTag?.eTag)
            assertNull(eTag?.weak)
        }
        assertTrue(called)

        val rq = mockEngine.requestHistory.last()
        assertEquals(HttpMethod.Put, rq.method)
        assertEquals("*", rq.headers[HttpHeaders.IfNoneMatch])
    }

    @Test
    fun `Put NEGATIVE TEST CASES precondition  If-Match, 412 Precondition Failed`() = runTest {
        val mockEngine = MockEngine { respond("", HttpStatusCode.PreconditionFailed) }
        val httpClient = HttpClient(mockEngine)
        val dav = DavResource(httpClient, sampleUrl)

        var called = false
        try {
            dav.put(
                { ByteReadChannel(sampleText) },
                ContentType.Text.Plain,
                headersOf(HttpHeaders.IfMatch, "\"ExistingETag\"")
            ) {
                called = true
            }
            fail("Expected PreconditionFailedException")
        } catch (e: HttpException) {
            if (e.statusCode != HttpStatusCode.PreconditionFailed.value)
                fail("Expected HTTP 412")
        }
        assertFalse(called)
        val rq = mockEngine.requestHistory.last()
        assertEquals("\"ExistingETag\"", rq.headers[HttpHeaders.IfMatch])
        assertNull(rq.headers[HttpHeaders.IfNoneMatch])
    }

    @Test
    fun Search() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = "<multistatus xmlns='DAV:'>" +
                        "  <response>" +
                        "    <href>/dav</href>" +
                        "    <propstat>" +
                        "      <prop>" +
                        "        <displayname>Found something</displayname>" +
                        "      </prop>" +
                        "    </propstat>" +
                        "  </response>" +
                        "</multistatus>",
                status = HttpStatusCode.MultiStatus,  // 207 Multi-Status
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Xml.withCharset(Charsets.UTF_8).toString())
            )
        }
        val httpClient = HttpClient(mockEngine)
        val dav = DavResource(httpClient, sampleUrl)
        var called = false

        dav.search("<TEST/>") { response, hrefRelation ->
            assertEquals(Response.HrefRelation.SELF, hrefRelation)
            assertEquals("Found something", response[DisplayName::class.java]?.displayName)
            called = true
        }
        assertTrue(called)

        val rq = mockEngine.requestHistory.last()
        val requestBodyText = rq.body as TextContent

        assertEquals(HttpMethod.parse("SEARCH"), rq.method)
        assertEquals(sampleUrl.encodedPath, rq.url.encodedPath)
        assertEquals("<TEST/>", requestBodyText.text)
    }


    /** test helpers **/

    @Test
    fun `AssertMultiStatus EmptyBody NoXML`() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = "",
                status = HttpStatusCode.MultiStatus  // 207 Multi-Status
            )
        }
        val httpClient = HttpClient(mockEngine)

        val dav = DavResource(httpClient, Url("https://from.com"))
        val response = httpClient.get(dav.location)
        dav.assertMultiStatus(response, response.bodyAsChannel())
    }

    @Test
    fun `AssertMultiStatus EmptyBody XML`() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = "",
                status = HttpStatusCode.MultiStatus,  // 207 Multi-Status
                headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Xml.toString())
            )
        }
        val httpClient = HttpClient(mockEngine)

        val dav = DavResource(httpClient, Url("https://from.com"))
        val response = httpClient.get(dav.location)
        dav.assertMultiStatus(response, response.bodyAsChannel())
    }

    @Test
    fun `AssertMultiStatus NonXML ButContentIsXML`() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = "<?xml version=\"1.0\"><test/>",
                status = HttpStatusCode.MultiStatus,  // 207 Multi-Status
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.OctetStream.toString())
            )
        }
        val httpClient = HttpClient(mockEngine)

        val dav = DavResource(httpClient, Url("https://from.com"))
        val response = httpClient.get(dav.location)
        dav.assertMultiStatus(response, response.bodyAsChannel())
    }

    @Test(expected = DavException::class)
    fun `AssertMultiStatus NonXML Really Not XML`() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = "Some error occurred",
                status = HttpStatusCode.MultiStatus,  // 207 Multi-Status
                headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Plain.toString())
            )
        }
        val httpClient = HttpClient(mockEngine)

        val dav = DavResource(httpClient, Url("https://from.com"))
        val response = httpClient.get(dav.location)
        dav.assertMultiStatus(response, response.bodyAsChannel())
    }

    @Test(expected = DavException::class)
    fun `AssertMultiStatus Not 207`() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = "",
                status = HttpStatusCode(403, "Multi-Status"),  // 207 Multi-Status
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Xml.toString())
            )
        }
        val httpClient = HttpClient(mockEngine)

        val dav = DavResource(httpClient, Url("https://from.com"))
        val response = httpClient.get(dav.location)
        dav.assertMultiStatus(response, response.bodyAsChannel())
    }

    @Test
    fun `AssertMultiStatus Ok ApplicationXml`() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = "",
                status = HttpStatusCode.MultiStatus,  // 207 Multi-Status
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Xml.toString())
            )
        }
        val httpClient = HttpClient(mockEngine)

        val dav = DavResource(httpClient, Url("https://from.com"))
        val response = httpClient.get(dav.location)
        dav.assertMultiStatus(response, response.bodyAsChannel())
    }

    @Test
    fun `AssertMultiStatus Ok TextXml`() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = "",
                status = HttpStatusCode.MultiStatus,  // 207 Multi-Status
                headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Xml.toString())
            )
        }
        val httpClient = HttpClient(mockEngine)
        val dav = DavResource(httpClient, Url("https://from.com"))
        val response = httpClient.get(dav.location)
        dav.assertMultiStatus(response, response.bodyAsChannel())
    }
}