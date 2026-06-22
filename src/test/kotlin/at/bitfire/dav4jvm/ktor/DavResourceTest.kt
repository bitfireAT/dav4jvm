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
import io.ktor.http.Headers
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.content.TextContent
import io.ktor.http.contentType
import io.ktor.http.fullPath
import io.ktor.http.headersOf
import io.ktor.http.withCharset
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
    private val sampleDestination = sampleUrl.resolve("test")

    private fun mockEngine(status: HttpStatusCode, content: String = "", headers: Headers = headersOf()) =
        MockEngine { respond(content, status, headers) }

    private fun getEngine(status: HttpStatusCode, eTag: String? = null, content: String = sampleText) =
        MockEngine {
            respond(content, status, HeadersBuilder().apply {
                eTag?.let { append(HttpHeaders.ETag, it) }
                append(HttpHeaders.ContentType, "application/x-test-result")
            }.build())
        }

    private fun propfindEngine(xmlResponse: String) =
        MockEngine {
            respond(
                xmlResponse, HttpStatusCode.MultiStatus,
                headersOf(HttpHeaders.ContentType, ContentType.Application.Xml.withCharset(Charsets.UTF_8).toString())
            )
        }

    private fun davResource(engine: MockEngine) = DavResource(HttpClient(engine), sampleUrl)

    private suspend fun assertMultiStatusOk(engine: MockEngine) {
        val client = HttpClient(engine)
        val dav = DavResource(client, Url("https://from.com"))
        val response = client.get(dav.location)
        dav.assertMultiStatus(response, response.bodyAsChannel())
    }


    @Test
    fun `copy 201 Created`() = runTest {
        val engine = mockEngine(HttpStatusCode.Created, sampleText, headersOf(HttpHeaders.ContentType, ContentType.Text.Plain.toString()))
        val dav = davResource(engine)
        var called = false
        dav.copy(sampleDestination, false) { called = true }
        assertTrue(called)
        with(engine.requestHistory.last()) {
            assertEquals(HttpMethod.parse("COPY"), method)
            assertEquals(sampleUrl.encodedPath, url.encodedPath)
            assertEquals(sampleDestination.toString(), headers[HttpHeaders.Destination])
            assertEquals("F", headers[HttpHeaders.Overwrite])
        }
    }

    @Test
    fun `copy 204 No Content`() = runTest {
        val engine = mockEngine(HttpStatusCode.NoContent, sampleText, headersOf(HttpHeaders.ContentType, ContentType.Text.Plain.toString()))
        val dav = davResource(engine)
        var called = false
        dav.copy(sampleDestination, true) { called = true }
        assertTrue(called)
        with(engine.requestHistory.last()) {
            assertEquals(HttpMethod.parse("COPY"), method)
            assertEquals(sampleDestination.toString(), headers[HttpHeaders.Destination])
            assertNull(headers[HttpHeaders.Overwrite])
        }
    }

    @Test
    fun `copy 207 Multi-Status throws HttpException`() = runTest {
        val dav = davResource(mockEngine(HttpStatusCode.MultiStatus, sampleText))
        var called = false
        try {
            dav.copy(sampleDestination, false) { called = true }
            fail("Expected HttpException")
        } catch (_: HttpException) {
            assertFalse(called)
        }
    }

    @Test
    fun `delete 204 No Content`() = runTest {
        val engine = mockEngine(HttpStatusCode.NoContent, sampleText)
        val dav = davResource(engine)
        var called = false
        dav.delete { called = true }
        assertTrue(called)
        with(engine.requestHistory.last()) {
            assertEquals(HttpMethod.Delete, method)
            assertEquals(sampleUrl.encodedPath, url.encodedPath)
            assertNull(headers[HttpHeaders.IfMatch])
        }
    }

    @Test
    fun `delete with If-Match header`() = runTest {
        val engine = MockEngine { respondOk(content = sampleText) }
        val dav = davResource(engine)
        var called = false
        dav.delete(headersOf("If-Match", "\"SomeETag\"")) { called = true }
        assertTrue(called)
        assertEquals("\"SomeETag\"", engine.requestHistory.last().headers[HttpHeaders.IfMatch])
    }

    @Test
    fun `delete follows 302 redirect`() = runTest {
        var numResponses = 0
        val engine = MockEngine {
            numResponses += 1
            when (numResponses) {
                1 -> respondRedirect("/new-location")
                else -> respondOk()
            }
        }
        val dav = davResource(engine)
        var called = false
        dav.delete(null) { called = true }
        assertTrue(called)
    }

    @Test
    fun `delete 207 Multi-Status throws HttpException`() = runTest {
        val dav = davResource(MockEngine { respondError(HttpStatusCode.MultiStatus) })
        var called = false
        try {
            dav.delete(null) { called = true }
            fail("Expected HttpException")
        } catch (_: HttpException) {
            assertFalse(called)
        }
    }

    @Test
    fun `followRedirects 302 Found updates location`() = runTest {
        var numResponses = 0
        val engine = MockEngine {
            numResponses += 1
            when (numResponses) {
                1 -> respond("New location!", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "https://to.com/"))
                else -> respond("", HttpStatusCode.NoContent, headersOf(HttpHeaders.Location, "https://to.com/"))
            }
        }
        val client = HttpClient(engine) { followRedirects = false }
        val dav = DavResource(client, sampleUrl)
        dav.followRedirects({ client.prepareGet("https://from.com/") }) { response ->
            assertEquals(HttpStatusCode.NoContent, response.status)
            assertEquals(Url("https://to.com/"), dav.location)
        }
    }

    @Test(expected = DavException::class)
    fun `followRedirects HTTPS to HTTP throws DavException`() = runTest {
        val engine = MockEngine {
            respond("New location!", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "http://to.com/"))
        }
        val client = HttpClient(engine) { followRedirects = false }
        val dav = DavResource(client, Url("https://from.com"))
        dav.followRedirects({ client.prepareGet("https://from.com/") }) {}
    }

    @Test
    fun `get 200 OK`() = runTest {
        val engine = getEngine(HttpStatusCode.OK, eTag = "W/\"My Weak ETag\"")
        val dav = davResource(engine)
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
        with(engine.requestHistory.last()) {
            assertEquals(HttpMethod.Get, method)
            assertEquals(sampleUrl.encodedPath, url.encodedPath)
            assertEquals(ContentType.Any.toString(), headers[HttpHeaders.Accept])
            assertEquals("identity", headers[HttpHeaders.AcceptEncoding])
        }
    }

    @Test
    fun `get disableCompression false sends no Accept-Encoding`() = runTest {
        val engine = getEngine(HttpStatusCode.OK)
        davResource(engine).get(disableCompression = false) {}
        assertNull(engine.requestHistory.last().headers[HttpHeaders.AcceptEncoding])
    }

    @Test
    fun `get follows 302 redirect`() = runTest {
        var numResponses = 0
        val engine = MockEngine {
            numResponses += 1
            when (numResponses) {
                1 -> respond("This resource was moved.", HttpStatusCode.TemporaryRedirect, headersOf(HttpHeaders.Location, "/target"))
                else -> respond(sampleText, HttpStatusCode.OK, headersOf(HttpHeaders.ETag, "\"StrongETag\""))
            }
        }
        val dav = davResource(engine)
        var called = false
        dav.get { response ->
            called = true
            assertEquals(sampleText, response.bodyAsText())
            val eTag = GetETag(response.headers[HttpHeaders.ETag])
            assertEquals("StrongETag", eTag.eTag)
            assertFalse(eTag.weak)
        }
        assertTrue(called)
        assertEquals("/target", engine.requestHistory.last().url.fullPath)
    }

    @Test
    fun `get 200 OK without ETag`() = runTest {
        val dav = davResource(getEngine(HttpStatusCode.OK))
        var called = false
        dav.get { response ->
            called = true
            assertNull(response.headers[HttpHeaders.ETag])
        }
        assertTrue(called)
    }

    @Test
    fun `getRange 206 Partial Content`() = runTest {
        val engine = mockEngine(HttpStatusCode.PartialContent)
        val dav = davResource(engine)
        var called = false
        dav.getRange(100, 342) { response ->
            assertEquals("bytes=100-441", response.request.headers[HttpHeaders.Range])
            called = true
        }
        assertTrue(called)
    }

    @Test
    fun `post 200 OK`() = runTest {
        val engine = MockEngine {
            respond(sampleText, HttpStatusCode.OK, HeadersBuilder().apply {
                append(HttpHeaders.ContentType, "application/x-test-result")
                append(HttpHeaders.ETag, "W/\"My Weak ETag\"")
            }.build())
        }
        val dav = davResource(engine)
        var called = false
        dav.post(TextContent("body", ContentType.parse("application/x-test-result"))) { response ->
            called = true
            assertEquals(sampleText, response.bodyAsText())
            val eTag = GetETag.fromHttpResponse(response)
            assertEquals("My Weak ETag", eTag!!.eTag)
            assertTrue(eTag.weak)
            assertEquals(ContentType.parse("application/x-test-result"), response.contentType())
        }
        assertTrue(called)
        with(engine.requestHistory.last()) {
            assertEquals(HttpMethod.Post, method)
            assertEquals(sampleUrl.encodedPath, url.encodedPath)
            assertEquals(ContentType.parse("application/x-test-result"), body.contentType)
        }
    }

    @Test
    fun `post follows 302 redirect`() = runTest {
        var numResponses = 0
        val engine = MockEngine {
            numResponses += 1
            when (numResponses) {
                1 -> respond("This resource was moved.", HttpStatusCode.TemporaryRedirect, headersOf(HttpHeaders.Location, "/target"))
                else -> respond(sampleText, HttpStatusCode.OK, headersOf(HttpHeaders.ETag, "\"StrongETag\""))
            }
        }
        val dav = davResource(engine)
        var called = false
        dav.post(TextContent("body", ContentType.parse("application/x-test-result"))) { response ->
            called = true
            assertEquals(sampleText, response.bodyAsText())
            val eTag = GetETag(response.headers[HttpHeaders.ETag])
            assertEquals("StrongETag", eTag.eTag)
            assertFalse(eTag.weak)
        }
        assertTrue(called)
        assertEquals("/target", engine.requestHistory.last().url.encodedPath)
    }

    @Test
    fun `post 200 OK without ETag`() = runTest {
        val dav = davResource(mockEngine(HttpStatusCode.OK, sampleText))
        var called = false
        dav.post(TextContent("body", ContentType.Text.Plain)) { response ->
            called = true
            assertNull(response.headers[HttpHeaders.ETag])
        }
        assertTrue(called)
    }

    @Test
    fun `move 201 Created`() = runTest {
        val engine = mockEngine(HttpStatusCode.Created)
        val dav = davResource(engine)
        var called = false
        dav.move(sampleDestination, false) { called = true }
        assertTrue(called)
        assertEquals(sampleDestination, dav.location)
        with(engine.requestHistory.last()) {
            assertEquals(HttpMethod.parse("MOVE"), method)
            assertEquals(sampleUrl.encodedPath, url.encodedPath)
            assertEquals(sampleDestination.toString(), headers[HttpHeaders.Destination])
            assertEquals("F", headers[HttpHeaders.Overwrite])
        }
    }

    @Test
    fun `move 204 No Content`() = runTest {
        val engine = mockEngine(HttpStatusCode.NoContent)
        val dav = davResource(engine)
        var called = false
        dav.move(sampleDestination, true) { called = true }
        assertTrue(called)
        assertEquals(sampleDestination, dav.location)
        with(engine.requestHistory.last()) {
            assertEquals(HttpMethod.parse("MOVE"), method)
            assertEquals(sampleDestination.toString(), headers[HttpHeaders.Destination])
            assertNull(headers[HttpHeaders.Overwrite])
        }
    }

    @Test
    fun `move 207 Multi-Status throws HttpException`() = runTest {
        val dav = davResource(mockEngine(HttpStatusCode.MultiStatus))
        var called = false
        try {
            dav.move(sampleDestination, false) { called = true }
            fail("Expected HttpException")
        } catch (_: HttpException) {
            assertFalse(called)
        }
    }

    @Test
    fun `mkCol 207 Multi-Status throws HttpException`() = runTest {
        val dav = davResource(mockEngine(HttpStatusCode.MultiStatus))
        var called = false
        try {
            dav.mkCol("") { called = true }
            fail("Expected HttpException")
        } catch (_: HttpException) {
            assertFalse(called)
        }
    }

    @Test
    fun `mkCol null body sends no Content-Type`() = runTest {
        val engine = mockEngine(HttpStatusCode.Created)
        val dav = davResource(engine)
        var called = false
        dav.mkCol(null) { called = true }
        assertTrue(called)
        with(engine.requestHistory.last()) {
            assertEquals(HttpMethod.parse("MKCOL"), method)
            assertNull(headers[HttpHeaders.ContentType])
        }
    }

    @Test
    fun `options with capabilities`() = runTest {
        val engine = MockEngine {
            respond("", HttpStatusCode.OK, HeadersBuilder().apply { append("DAV", "  1,  2 ,3,hyperactive-access") }.build())
        }
        val dav = davResource(engine)
        var called = false
        dav.options { davCapabilities, _ ->
            called = true
            assertTrue(davCapabilities.any { it.contains("1") })
            assertTrue(davCapabilities.any { it.contains("2") })
            assertTrue(davCapabilities.any { it.contains("3") })
            assertTrue(davCapabilities.any { it.contains("hyperactive-access") })
        }
        assertTrue(called)
        assertEquals("identity", engine.requestHistory.last().headers[HttpHeaders.AcceptEncoding])
    }

    @Test
    fun `options without capabilities`() = runTest {
        val dav = davResource(MockEngine { respondOk() })
        var called = false
        dav.options { davCapabilities, _ ->
            called = true
            assertTrue(davCapabilities.isEmpty())
        }
        assertTrue(called)
    }

    @Test
    fun `propfind 500 Internal Server Error throws HttpException`() = runTest {
        val dav = davResource(MockEngine { respondError(HttpStatusCode.InternalServerError) })
        var called = false
        try {
            dav.propfind(0, WebDAV.ResourceType) { _, _ -> called = true }
            fail("Expected HttpException")
        } catch (_: HttpException) {
            assertFalse(called)
        }
    }

    @Test
    fun `propfind 200 OK throws DavException`() = runTest {
        val dav = davResource(MockEngine { respondOk() })
        var called = false
        try {
            dav.propfind(0, WebDAV.ResourceType) { _, _ -> called = true }
            fail("Expected DavException")
        } catch (_: DavException) {
            assertFalse(called)
        }
    }

    @Test
    fun `propfind non-XML response throws DavException`() = runTest {
        val dav = davResource(MockEngine {
            respond("<html></html>", HttpStatusCode.MultiStatus, headersOf(HttpHeaders.ContentType, ContentType.Text.Html.toString()))
        })
        var called = false
        try {
            dav.propfind(0, WebDAV.ResourceType) { _, _ -> called = true }
            fail("Expected DavException")
        } catch (_: DavException) {
            assertFalse(called)
        }
    }

    @Test
    fun `propfind malformed XML throws DavException`() = runTest {
        val dav = davResource(MockEngine {
            respond(
                "<malformed-xml>", HttpStatusCode.MultiStatus,
                headersOf(HttpHeaders.ContentType, ContentType.Application.Xml.withCharset(Charsets.UTF_8).toString())
            )
        })
        var called = false
        try {
            dav.propfind(0, WebDAV.ResourceType) { _, _ -> called = true }
            fail("Expected DavException")
        } catch (_: DavException) {
            assertFalse(called)
        }
    }

    @Test
    fun `propfind no multistatus root throws DavException`() = runTest {
        val dav = davResource(propfindEngine("<test></test>"))
        var called = false
        try {
            dav.propfind(0, WebDAV.ResourceType) { _, _ -> called = true }
            fail("Expected DavException")
        } catch (_: DavException) {
            assertFalse(called)
        }
    }

    @Test
    fun `propfind invalid response status mapped to 500`() = runTest {
        val dav = davResource(
            propfindEngine(
                "<multistatus xmlns='DAV:'>" +
                        "  <response>" +
                        "    <href>/dav</href>" +
                        "    <status>Invalid Status Line</status>" +
                        "  </response>" +
                        "</multistatus>"
            )
        )
        var called = false
        dav.propfind(0, WebDAV.ResourceType) { response, relation ->
            assertEquals(Response.HrefRelation.SELF, relation)
            assertEquals(HttpStatusCode.InternalServerError, response.status)
            called = true
        }
        assertTrue(called)
    }

    @Test
    fun `propfind failure response status`() = runTest {
        val dav = davResource(
            propfindEngine(
                "<multistatus xmlns='DAV:'>" +
                        "  <response>" +
                        "    <href>/dav</href>" +
                        "    <status>HTTP/1.1 403 Forbidden</status>" +
                        "  </response>" +
                        "</multistatus>"
            )
        )
        var called = false
        dav.propfind(0, WebDAV.ResourceType) { response, relation ->
            assertEquals(Response.HrefRelation.SELF, relation)
            assertEquals(HttpStatusCode.Forbidden, response.status)
            called = true
        }
        assertTrue(called)
    }

    @Test
    fun `propfind invalid propstat status assumed OK`() = runTest {
        val dav = davResource(
            propfindEngine(
                "<multistatus xmlns='DAV:'>" +
                        "  <response>" +
                        "    <href>/dav</href>" +
                        "    <propstat>" +
                        "      <prop><resourcetype><collection/></resourcetype></prop>" +
                        "      <status>Invalid Status Line</status>" +
                        "    </propstat>" +
                        "  </response>" +
                        "</multistatus>"
            )
        )
        var called = false
        dav.propfind(0, WebDAV.ResourceType) { response, relation ->
            called = true
            assertEquals(Response.HrefRelation.SELF, relation)
            assertTrue(response.properties.filterIsInstance<ResourceType>().isEmpty())
        }
        assertTrue(called)
    }

    @Test
    fun `propfind no response elements callback not called`() = runTest {
        val dav = davResource(propfindEngine("<multistatus xmlns='DAV:'></multistatus>"))
        dav.propfind(0, WebDAV.ResourceType) { _, _ -> fail("Shouldn't be called") }
    }

    @Test
    fun `propfind success response status`() = runTest {
        val dav = davResource(
            propfindEngine(
                "<multistatus xmlns='DAV:'>" +
                        "  <response>" +
                        "    <href>/dav</href>" +
                        "    <status>HTTP/1.1 200 OK</status>" +
                        "  </response>" +
                        "</multistatus>"
            )
        )
        var called = false
        dav.propfind(0, WebDAV.ResourceType) { response, relation ->
            called = true
            assertTrue(response.isSuccess())
            assertEquals(Response.HrefRelation.SELF, relation)
            assertEquals(0, response.properties.size)
        }
        assertTrue(called)
    }

    @Test
    fun `propfind response with propstat`() = runTest {
        val dav = davResource(
            propfindEngine(
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
                        "</multistatus>"
            )
        )
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
    fun `propfind collection with multiple members`() = runTest {
        val dav = davResource(
            propfindEngine(
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
                        "      <prop><displayname>Absolute path with @ and :</displayname></prop>" +
                        "      <status>HTTP/1.1 200 OK</status>" +
                        "    </propstat>" +
                        "  </response>" +
                        "  <response>" +
                        "    <href>relative-uid@host.file</href>" +
                        "    <propstat>" +
                        "      <prop><displayname>Relative path with @</displayname></prop>" +
                        "      <status>HTTP/1.1 200 OK</status>" +
                        "    </propstat>" +
                        "  </response>" +
                        "  <response>" +
                        "    <href>relative:colon.vcf</href>" +
                        "    <propstat>" +
                        "      <prop><displayname>Relative path with colon</displayname></prop>" +
                        "      <status>HTTP/1.1 200 OK</status>" +
                        "    </propstat>" +
                        "  </response>" +
                        "  <response>" +
                        "    <href>/something-very/else</href>" +
                        "    <propstat>" +
                        "      <prop><displayname>Not requested</displayname></prop>" +
                        "      <status>HTTP/1.1 200 OK</status>" +
                        "    </propstat>" +
                        "  </response>" +
                        "</multistatus>"
            )
        )
        var nrCalled = 0
        dav.propfind(1, WebDAV.ResourceType, WebDAV.DisplayName) { response, relation ->
            when (response.href) {
                sampleUrl.resolve("/dav/") -> {
                    assertTrue(response.isSuccess())
                    assertEquals(Response.HrefRelation.SELF, relation)
                    assertTrue(response[ResourceType::class.java]!!.types.contains(WebDAV.Collection))
                    assertEquals("My DAV Collection", response[DisplayName::class.java]?.displayName)
                    nrCalled++
                }

                sampleUrl.resolve("/dav/subcollection/") -> {
                    assertTrue(response.isSuccess())
                    assertEquals(Response.HrefRelation.MEMBER, relation)
                    assertTrue(response[ResourceType::class.java]!!.types.contains(WebDAV.Collection))
                    assertEquals("A Subfolder", response[DisplayName::class.java]?.displayName)
                    nrCalled++
                }

                sampleUrl.resolve("/dav/uid@host:file") -> {
                    assertTrue(response.isSuccess())
                    assertEquals(Response.HrefRelation.MEMBER, relation)
                    assertEquals("Absolute path with @ and :", response[DisplayName::class.java]?.displayName)
                    nrCalled++
                }

                sampleUrl.resolve("/dav/relative-uid@host.file") -> {
                    assertTrue(response.isSuccess())
                    assertEquals(Response.HrefRelation.MEMBER, relation)
                    assertEquals("Relative path with @", response[DisplayName::class.java]?.displayName)
                    nrCalled++
                }

                sampleUrl.resolve("/dav/relative:colon.vcf") -> {
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
    fun `propfind duplicate property in different propstats uses 200 OK value`() = runTest {
        val dav = davResource(
            propfindEngine(
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
                        "      <prop><resourcetype/></prop>" +
                        "      <status>HTTP/1.1 404 Not Found</status>" +
                        "    </propstat>" +
                        "  </response>" +
                        "</multistatus>"
            )
        )
        var called = false
        dav.propfind(0, WebDAV.ResourceType, WebDAV.DisplayName) { response, relation ->
            called = true
            assertTrue(response.isSuccess())
            assertEquals(Response.HrefRelation.SELF, relation)
            assertEquals(sampleUrl.resolve("/dav/"), response.href)
            assertTrue(response[ResourceType::class.java]!!.types.contains(WebDAV.Collection))
            assertEquals("My DAV Collection", response[DisplayName::class.java]?.displayName)
        }
        assertTrue(called)
    }

    @Test
    fun `propfind propstat without status assumes 200 OK`() = runTest {
        val dav = davResource(
            propfindEngine(
                "<multistatus xmlns='DAV:'>" +
                        "  <response>" +
                        "    <href>/dav</href>" +
                        "    <propstat>" +
                        "      <prop><displayname>Without Status</displayname></prop>" +
                        "    </propstat>" +
                        "  </response>" +
                        "</multistatus>"
            )
        )
        var called = false
        dav.propfind(0, WebDAV.DisplayName) { response, _ ->
            called = true
            assertEquals(200, response.propstat.first().status.value)
            assertEquals("Without Status", response[DisplayName::class.java]?.displayName)
        }
        assertTrue(called)
    }

    @Test
    fun `proppatch response with propstat`() = runTest {
        val dav = davResource(
            propfindEngine(
                "<multistatus xmlns='DAV:' xmlns:s='sample'>" +
                        "  <response>" +
                        "    <href>/dav</href>" +
                        "    <propstat>" +
                        "      <prop><s:setThis>Some Value</s:setThis></prop>" +
                        "      <status>HTTP/1.1 200 OK</status>" +
                        "    </propstat>" +
                        "    <propstat>" +
                        "      <prop><s:removeThis/></prop>" +
                        "      <status>HTTP/1.1 404 Not Found</status>" +
                        "    </propstat>" +
                        "  </response>" +
                        "</multistatus>"
            )
        )
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
    fun `proppatch request body format`() = runTest {
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
    fun `put 201 Created`() = runTest {
        val engine = MockEngine { respond(" ", HttpStatusCode.Created, headersOf(HttpHeaders.ETag, "W/\"Weak PUT ETag\"")) }
        val dav = davResource(engine)
        var called = false
        dav.put(TextContent(sampleText, ContentType.Text.Plain)) { response ->
            called = true
            val eTag = GetETag.fromHttpResponse(response)!!
            assertEquals("Weak PUT ETag", eTag.eTag)
            assertTrue(eTag.weak)
            assertEquals(response.request.url, dav.location)
        }
        assertTrue(called)
        with(engine.requestHistory.last()) {
            assertEquals(HttpMethod.Put, method)
            assertEquals(sampleUrl.encodedPath, url.encodedPath)
            assertNull(headers[HttpHeaders.IfMatch])
            assertNull(headers[HttpHeaders.IfNoneMatch])
        }
    }

    @Test
    fun `put If-None-Match follows 301 redirect`() = runTest {
        var numberOfResponse = 0
        val engine = MockEngine {
            numberOfResponse += 1
            when (numberOfResponse) {
                1 -> respond("", HttpStatusCode.MovedPermanently, headersOf(HttpHeaders.Location, "/target"))
                else -> respond("", HttpStatusCode.NoContent)
            }
        }
        val dav = davResource(engine)
        var called = false
        dav.put(TextContent(sampleText, ContentType.Text.Plain), headersOf(HttpHeaders.IfNoneMatch, "*")) { response ->
            called = true
            assertEquals(sampleUrl.resolve("/target"), response.request.url)
            assertNull(GetETag.fromHttpResponse(response))
        }
        assertTrue(called)
        assertEquals("*", engine.requestHistory.last().headers[HttpHeaders.IfNoneMatch])
    }

    @Test
    fun `put 412 Precondition Failed throws HttpException`() = runTest {
        val engine = mockEngine(HttpStatusCode.PreconditionFailed)
        val dav = davResource(engine)
        var called = false
        try {
            dav.put(TextContent(sampleText, ContentType.Text.Plain), headersOf(HttpHeaders.IfMatch, "\"ExistingETag\"")) {
                called = true
            }
            fail("Expected HttpException")
        } catch (e: HttpException) {
            if (e.statusCode != HttpStatusCode.PreconditionFailed.value)
                fail("Expected HTTP 412")
        }
        assertFalse(called)
        with(engine.requestHistory.last()) {
            assertEquals("\"ExistingETag\"", headers[HttpHeaders.IfMatch])
            assertNull(headers[HttpHeaders.IfNoneMatch])
        }
    }

    @Test
    fun `search request body and response`() = runTest {
        val engine = propfindEngine(
            "<multistatus xmlns='DAV:'>" +
                    "  <response>" +
                    "    <href>/dav</href>" +
                    "    <propstat>" +
                    "      <prop><displayname>Found something</displayname></prop>" +
                    "    </propstat>" +
                    "  </response>" +
                    "</multistatus>"
        )
        val dav = davResource(engine)
        var called = false
        dav.search("<TEST/>") { response, hrefRelation ->
            assertEquals(Response.HrefRelation.SELF, hrefRelation)
            assertEquals("Found something", response[DisplayName::class.java]?.displayName)
            called = true
        }
        assertTrue(called)
        with(engine.requestHistory.last()) {
            assertEquals(HttpMethod.parse("SEARCH"), method)
            assertEquals(sampleUrl.encodedPath, url.encodedPath)
            assertEquals("<TEST/>", (body as TextContent).text)
        }
    }


    /** assertMultiStatus helpers **/

    @Test
    fun `assertMultiStatus empty body no XML ok`() = runTest {
        assertMultiStatusOk(mockEngine(HttpStatusCode.MultiStatus))
    }

    @Test
    fun `assertMultiStatus empty body XML ok`() = runTest {
        assertMultiStatusOk(
            mockEngine(
                HttpStatusCode.MultiStatus, "",
                headersOf(HttpHeaders.ContentType, ContentType.Text.Xml.toString())
            )
        )
    }

    @Test
    fun `assertMultiStatus non-XML Content-Type but XML body ok`() = runTest {
        assertMultiStatusOk(
            mockEngine(
                HttpStatusCode.MultiStatus, "<?xml version=\"1.0\"><test/>",
                headersOf(HttpHeaders.ContentType, ContentType.Application.OctetStream.toString())
            )
        )
    }

    @Test(expected = DavException::class)
    fun `assertMultiStatus non-XML Content-Type non-XML body throws DavException`() = runTest {
        assertMultiStatusOk(
            mockEngine(
                HttpStatusCode.MultiStatus, "Some error occurred",
                headersOf(HttpHeaders.ContentType, ContentType.Text.Plain.toString())
            )
        )
    }

    @Test(expected = DavException::class)
    fun `assertMultiStatus non-207 status throws DavException`() = runTest {
        assertMultiStatusOk(
            mockEngine(
                HttpStatusCode(403, "Multi-Status"), "",
                headersOf(HttpHeaders.ContentType, ContentType.Application.Xml.toString())
            )
        )
    }

    @Test
    fun `assertMultiStatus application xml ok`() = runTest {
        assertMultiStatusOk(
            mockEngine(
                HttpStatusCode.MultiStatus, "",
                headersOf(HttpHeaders.ContentType, ContentType.Application.Xml.toString())
            )
        )
    }

    @Test
    fun `assertMultiStatus text xml ok`() = runTest {
        assertMultiStatusOk(
            mockEngine(
                HttpStatusCode.MultiStatus, "",
                headersOf(HttpHeaders.ContentType, ContentType.Text.Xml.toString())
            )
        )
    }

}
