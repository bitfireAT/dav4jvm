/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

package at.bitfire.dav4jvm.okhttp

import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.okhttp.exception.DavException
import at.bitfire.dav4jvm.okhttp.exception.HttpException
import at.bitfire.dav4jvm.okhttp.exception.PreconditionFailedException
import at.bitfire.dav4jvm.property.webdav.DisplayName
import at.bitfire.dav4jvm.property.webdav.GetContentType
import at.bitfire.dav4jvm.property.webdav.GetETag
import at.bitfire.dav4jvm.property.webdav.ResourceType
import at.bitfire.dav4jvm.property.webdav.WebDAV
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.net.HttpURLConnection

class DavResourceTest {

    private val sampleText = "SAMPLE RESPONSE"

    private val httpClient = OkHttpClient.Builder()
            .followRedirects(false)
            .build()
    private val mockServer = MockWebServer()


    @Before
    fun startServer() {
        mockServer.start()
    }

    @After
    fun stopServer() {
        mockServer.close()
    }

    private fun sampleUrl() = mockServer.url("/dav/")


    @Test
    fun testCopy() {
        val url = sampleUrl()
        val destination = url.resolve("test")!!

        /* POSITIVE TEST CASES */

        // no preconditions, 201 Created, resulted in the creation of a new resource
        mockServer.enqueue(MockResponse.Builder()
            .code(HttpURLConnection.HTTP_CREATED)
            .build())
        var called = false
        DavResource(httpClient, url).let { dav ->
            dav.copy(destination, false) {
                called = true
            }
            assertTrue(called)
        }

        var rq = mockServer.takeRequest()
        assertEquals("COPY", rq.method)
        assertEquals(url, rq.url)
        assertEquals(destination.toString(), rq.headers["Destination"])
        assertEquals("F", rq.headers["Overwrite"])

        // no preconditions, 204 No content, resource successfully copied to a preexisting
        // destination resource
        mockServer.enqueue(MockResponse.Builder()
            .code(HttpURLConnection.HTTP_NO_CONTENT)
            .build())
        called = false
        DavResource(httpClient, url).let { dav ->
            dav.copy(destination, true) {
                called = true
            }
            assertTrue(called)
        }

        rq = mockServer.takeRequest()
        assertEquals("COPY", rq.method)
        assertEquals(url, rq.url)
        assertEquals(destination.toString(), rq.headers["Destination"])
        assertNull(rq.headers["Overwrite"])

        /* NEGATIVE TEST CASES */

        // 207 multi-status (e.g. errors on some of resources affected by
        // the COPY prevented the operation from taking place)

        mockServer.enqueue(MockResponse.Builder()
            .code(207)
            .build())
        try {
            called = false
            DavResource(httpClient, url).let { dav ->
                dav.copy(destination, false) { called = true }
                fail("Expected HttpException")
            }
        } catch(_: HttpException) {
            assertFalse(called)
        }
    }

    @Test
    fun testDelete() {
        val url = sampleUrl()
        val dav = DavResource(httpClient, url)

        /* POSITIVE TEST CASES */

        // no preconditions, 204 No Content
        mockServer.enqueue(MockResponse.Builder()
            .code(HttpURLConnection.HTTP_NO_CONTENT)
            .build())
        var called = false
        dav.delete {
            called = true
        }
        assertTrue(called)

        var rq = mockServer.takeRequest()
        assertEquals("DELETE", rq.method)
        assertEquals(url, rq.url)
        assertNull(rq.headers["If-Match"])

        // precondition: If-Match / If-Schedule-Tag-Match, 200 OK
        mockServer.enqueue(MockResponse.Builder()
            .code(HttpURLConnection.HTTP_OK)
            .body("Resource has been deleted.")
            .build())
        called = false
        dav.delete("DeleteOnlyThisETag", "DeleteOnlyThisScheduleTag") {
            called = true
        }
        assertTrue(called)

        rq = mockServer.takeRequest()
        assertEquals("\"DeleteOnlyThisETag\"", rq.headers["If-Match"])
        assertEquals("\"DeleteOnlyThisScheduleTag\"", rq.headers["If-Schedule-Tag-Match"])

        // 302 Moved Temporarily
        mockServer.enqueue(MockResponse.Builder()
            .code(HttpURLConnection.HTTP_MOVED_TEMP)
            .setHeader("Location", "/new-location")
            .build()
        )
        mockServer.enqueue(MockResponse.Builder()
            .code(HttpURLConnection.HTTP_OK)
            .build())
        called = false
        dav.delete(null) {
            called = true
        }
        assertTrue(called)

        /* NEGATIVE TEST CASES */

        // 207 multi-status (e.g. single resource couldn't be deleted when DELETEing a collection)
        mockServer.enqueue(MockResponse.Builder()
            .code(207)
            .build())
        try {
            called = false
            dav.delete(null) { called = true }
            fail("Expected HttpException")
        } catch(_: HttpException) {
            assertFalse(called)
        }
    }

    @Test
    fun testFollowRedirects_302() {
        val url = sampleUrl()
        val dav = DavResource(httpClient, url)
        var i = 0
        dav.followRedirects {
            if (i++ == 0)
                Response.Builder()
                    .protocol(Protocol.HTTP_1_1)
                    .code(302)
                    .message("Found")
                    .header("Location", "http://to.com/")
                    .request(Request.Builder()
                        .get()
                        .url("http://from.com/")
                        .build())
                    .body("New location!".toResponseBody())
                    .build()
            else
                Response.Builder()
                    .protocol(Protocol.HTTP_1_1)
                    .code(204)
                    .message("No Content")
                    .request(Request.Builder()
                        .get()
                        .url("http://to.com/")
                        .build())
                    .build()
        }.let { response ->
            assertEquals(204, response.code)
            assertEquals("http://to.com/".toHttpUrl(), dav.location)
        }
    }

    @Test(expected = DavException::class)
    fun testFollowRedirects_HttpsToHttp() {
        val dav = DavResource(httpClient, "https://from.com".toHttpUrl())
        dav.followRedirects {
            Response.Builder()
                .protocol(Protocol.HTTP_1_1)
                .code(302)
                .message("Found")
                .header("Location", "http://to.com/")
                .request(Request.Builder()
                    .get()
                    .url("https://from.com/")
                    .build())
                .body("New location!".toResponseBody())
                .build()
        }
    }

    @Test
    fun testGet() {
        val url = sampleUrl()
        val dav = DavResource(httpClient, url)

        /* POSITIVE TEST CASES */

        // 200 OK
        mockServer.enqueue(MockResponse.Builder()
            .code(HttpURLConnection.HTTP_OK)
            .setHeader("ETag", "W/\"My Weak ETag\"")
            .setHeader("Content-Type", "application/x-test-result")
            .body(sampleText)
            .build())
        var called = false
        dav.get("*/*", null) { response ->
            called = true
            assertEquals(sampleText, response.body.string())

            val eTag = GetETag.fromResponse(response)
            assertEquals("My Weak ETag", eTag!!.eTag)
            assertTrue(eTag.weak)
            assertEquals("application/x-test-result", GetContentType(response.body.contentType()?.toString()).type)
        }
        assertTrue(called)

        var rq = mockServer.takeRequest()
        assertEquals("GET", rq.method)
        assertEquals(url, rq.url)
        assertEquals("*/*", rq.headers["Accept"])

        // 302 Moved Temporarily + 200 OK
        mockServer.enqueue(MockResponse.Builder()
            .code(HttpURLConnection.HTTP_MOVED_TEMP)
            .setHeader("Location", "/target")
            .body("This resource was moved.")
            .build())
        mockServer.enqueue(MockResponse.Builder()
            .code(HttpURLConnection.HTTP_OK)
            .setHeader("ETag", "\"StrongETag\"")
            .body(sampleText)
            .build())
        called = false
        dav.get("*/*", null) { response ->
            called = true
            assertEquals(sampleText, response.body.string())
            val eTag = GetETag(response.header("ETag")!!)
            assertEquals("StrongETag", eTag.eTag)
            assertFalse(eTag.weak)
        }
        assertTrue(called)

        mockServer.takeRequest()
        rq = mockServer.takeRequest()
        assertEquals("GET", rq.method)
        assertEquals("/target", rq.url.encodedPath)

        // 200 OK without ETag in response
        mockServer.enqueue(MockResponse.Builder()
            .code(HttpURLConnection.HTTP_OK)
            .body(sampleText)
            .build())
        called = false
        dav.get("*/*", null) { response ->
            called = true
            assertNull(response.header("ETag"))
        }
        assertTrue(called)
    }

    @Test
    fun testGetRange_Ok() {
        val url = sampleUrl()
        val dav = DavResource(httpClient, url)

        mockServer.enqueue(MockResponse.Builder()
            .code(HttpURLConnection.HTTP_PARTIAL)
            .build())
        var called = false
        dav.getRange("*/*", 100, 342) { response ->
            assertEquals("bytes=100-441", response.request.header("Range"))
            called = true
        }
        assertTrue(called)
    }

    @Test
    fun testPost() {
        val url = sampleUrl()
        val dav = DavResource(httpClient, url)

        /* POSITIVE TEST CASES */

        // 200 OK
        mockServer.enqueue(
            MockResponse.Builder()
                .code(HttpURLConnection.HTTP_OK)
                .setHeader("ETag", "W/\"My Weak ETag\"")
                .setHeader("Content-Type", "application/x-test-result")
                .body(sampleText)
                .build()
        )
        var called = false
        dav.post(
            body = "body".toRequestBody("application/x-test-result".toMediaType())
        ) { response ->
            called = true
            assertEquals(sampleText, response.body.string())

            val eTag = GetETag.fromResponse(response)
            assertEquals("My Weak ETag", eTag!!.eTag)
            assertTrue(eTag.weak)
            assertEquals("application/x-test-result", GetContentType(response.body.contentType()?.toString()).type)
        }
        assertTrue(called)

        var rq = mockServer.takeRequest()
        assertEquals("POST", rq.method)
        assertEquals(url, rq.url)
        assertTrue(rq.headers["Content-Type"]?.contains("application/x-test-result") == true)
        assertEquals("body", rq.body?.utf8())

        // 302 Moved Temporarily + 200 OK
        mockServer.enqueue(
            MockResponse.Builder()
                .code(HttpURLConnection.HTTP_MOVED_TEMP)
                .setHeader("Location", "/target")
                .body("This resource was moved.")
                .build()
        )
        mockServer.enqueue(
            MockResponse.Builder()
                .code(HttpURLConnection.HTTP_OK)
                .setHeader("ETag", "\"StrongETag\"")
                .body(sampleText)
                .build()
        )
        called = false
        dav.post(
            body = "body".toRequestBody("application/x-test-result".toMediaType())
        ) { response ->
            called = true
            assertEquals(sampleText, response.body.string())
            val eTag = GetETag(response.header("ETag")!!)
            assertEquals("StrongETag", eTag.eTag)
            assertFalse(eTag.weak)
        }
        assertTrue(called)

        mockServer.takeRequest()
        rq = mockServer.takeRequest()
        assertEquals("POST", rq.method)
        assertEquals("/target", rq.url.encodedPath)

        // 200 OK without ETag in response
        mockServer.enqueue(
            MockResponse.Builder()
                .code(HttpURLConnection.HTTP_OK)
                .body(sampleText)
                .build()
        )
        called = false
        dav.post(
            body = "body".toRequestBody("application/x-test-result".toMediaType())
        ) { response ->
            called = true
            assertNull(response.header("ETag"))
        }
        assertTrue(called)
    }

    @Test
    fun testMove() {
        val url = sampleUrl()
        val destination = url.resolve("test")!!

        /* POSITIVE TEST CASES */

        // no preconditions, 201 Created, new URL mapping at the destination
        mockServer.enqueue(
            MockResponse.Builder()
                .code(HttpURLConnection.HTTP_CREATED)
                .build()
        )
        var called = false
        DavResource(httpClient, url).let { dav ->
            dav.move(destination, false) {
                called = true
            }
            assertTrue(called)
            assertEquals(destination, dav.location)
        }

        var rq = mockServer.takeRequest()
        assertEquals("MOVE", rq.method)
        assertEquals(url, rq.url)
        assertEquals(destination.toString(), rq.headers["Destination"])
        assertEquals("F", rq.headers["Overwrite"])

        // no preconditions, 204 No content, URL already mapped, overwrite
        mockServer.enqueue(
            MockResponse.Builder()
                .code(HttpURLConnection.HTTP_NO_CONTENT)
                .build()
        )
        called = false
        DavResource(httpClient, url).let { dav ->
            dav.move(destination, true) {
                called = true
            }
            assertTrue(called)
            assertEquals(destination, dav.location)
        }

        rq = mockServer.takeRequest()
        assertEquals("MOVE", rq.method)
        assertEquals(url, rq.url)
        assertEquals(destination.toString(), rq.headers["Destination"])
        assertNull(rq.headers["Overwrite"])

        /* NEGATIVE TEST CASES */

        // 207 multi-status (e.g. errors on some of resources affected by
        // the MOVE prevented the operation from taking place)

        mockServer.enqueue(
            MockResponse.Builder()
                .code(207)
                .build()
        )
        try {
            called = false
            DavResource(httpClient, url).let { dav ->
                dav.move(destination, false) { called = true }
                fail("Expected HttpException")
            }
        } catch(_: HttpException) {
            assertFalse(called)
        }
    }

    @Test
    fun testOptions() {
        val url = sampleUrl()
        val dav = DavResource(httpClient, url)

        mockServer.enqueue(
            MockResponse.Builder()
                .code(HttpURLConnection.HTTP_OK)
                .setHeader("DAV", "  1,  2 ,3,hyperactive-access")
                .build()
        )
        var called = false
        dav.options { davCapabilities, _ ->
            called = true
            assertTrue(davCapabilities.contains("1"))
            assertTrue(davCapabilities.contains("2"))
            assertTrue(davCapabilities.contains("3"))
            assertTrue(davCapabilities.contains("hyperactive-access"))
        }
        assertTrue(called)

        mockServer.enqueue(
            MockResponse.Builder()
                .code(HttpURLConnection.HTTP_OK)
                .build()
        )
        called = false
        dav.options { davCapabilities, _ ->
            called = true
            assertTrue(davCapabilities.isEmpty())
        }
        assertTrue(called)
    }

    @Test
    fun testPropfindAndMultiStatus() {
        val url = sampleUrl()
        val dav = DavResource(httpClient, url)

        /*** NEGATIVE TESTS ***/

        // test for non-multi-status responses:
        // * 500 Internal Server Error
        mockServer.enqueue(
            MockResponse.Builder()
                .code(HttpURLConnection.HTTP_INTERNAL_ERROR)
                .build()
        )
        var called = false
        try {
            dav.propfind(0, WebDAV.ResourceType) { _, _ -> called = true }
            fail("Expected HttpException")
        } catch(_: HttpException) {
            assertFalse(called)
        }
        // * 200 OK (instead of 207 Multi-Status)
        mockServer.enqueue(
            MockResponse.Builder()
                .code(HttpURLConnection.HTTP_OK)
                .build()
        )
        try {
            called = false
            dav.propfind(0, WebDAV.ResourceType) { _, _ -> called = true }
            fail("Expected DavException")
        } catch(_: DavException) {
            assertFalse(called)
        }

        // test for invalid multi-status responses:
        // * non-XML response
        mockServer.enqueue(
            MockResponse.Builder()
                .code(207)
                .setHeader("Content-Type", "text/html")
                .body("<html></html>")
                .build()
        )
        try {
            called = false
            dav.propfind(0, WebDAV.ResourceType) { _, _ -> called = true }
            fail("Expected DavException")
        } catch(_: DavException) {
            assertFalse(called)
        }

        // * malformed XML response
        mockServer.enqueue(
            MockResponse.Builder()
                .code(207)
                .setHeader("Content-Type", "application/xml; charset=utf-8")
                .body("<malformed-xml>")
                .build()
        )
        try {
            called = false
            dav.propfind(0, WebDAV.ResourceType) { _, _ -> called = true }
            fail("Expected DavException")
        } catch(_: DavException) {
            assertFalse(called)
        }

        // * response without <multistatus> root element
        mockServer.enqueue(
            MockResponse.Builder()
                .code(207)
                .setHeader("Content-Type", "application/xml; charset=utf-8")
                .body("<test></test>")
                .build()
        )
        try {
            called = false
            dav.propfind(0, WebDAV.ResourceType) { _, _ -> called = true }
            fail("Expected DavException")
        } catch(_: DavException) {
            assertFalse(called)
        }

        // * multi-status response with invalid <status> in <response>
        mockServer.enqueue(
            MockResponse.Builder()
                .code(207)
                .setHeader("Content-Type", "application/xml; charset=utf-8")
                .body(
                    "<multistatus xmlns='DAV:'>" +
                            "  <response>" +
                            "    <href>/dav</href>" +
                            "    <status>Invalid Status Line</status>" +
                            "  </response>" +
                            "</multistatus>"
                )
                .build()
        )
        called = false
        dav.propfind(0, WebDAV.ResourceType) { response, relation ->
            assertEquals(at.bitfire.dav4jvm.okhttp.Response.HrefRelation.SELF, relation)
            assertEquals(500, response.status?.code)
            called = true
        }
        assertTrue(called)

        // * multi-status response with <response>/<status> element indicating failure
        mockServer.enqueue(
            MockResponse.Builder()
                .code(207)
                .setHeader("Content-Type", "application/xml; charset=utf-8")
                .body(
                    "<multistatus xmlns='DAV:'>" +
                            "  <response>" +
                            "    <href>/dav</href>" +
                            "    <status>HTTP/1.1 403 Forbidden</status>" +
                            "  </response>" +
                            "</multistatus>"
                )
                .build()
        )
        called = false
        dav.propfind(0, WebDAV.ResourceType) { response, relation ->
            assertEquals(at.bitfire.dav4jvm.okhttp.Response.HrefRelation.SELF, relation)
            assertEquals(403, response.status?.code)
            called = true
        }
        assertTrue(called)

        // * multi-status response with invalid <status> in <propstat>
        mockServer.enqueue(
            MockResponse.Builder()
                .code(207)
                .setHeader("Content-Type", "application/xml; charset=utf-8")
                .body(
                    "<multistatus xmlns='DAV:'>" +
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
                )
                .build()
        )
        called = false
        dav.propfind(0, WebDAV.ResourceType) { response, relation ->
            called = true
            assertEquals(at.bitfire.dav4jvm.okhttp.Response.HrefRelation.SELF, relation)
            assertTrue(response.properties.filterIsInstance<ResourceType>().isEmpty())
        }
        assertTrue(called)


        /*** POSITIVE TESTS ***/

        // multi-status response without <response> elements
        mockServer.enqueue(
            MockResponse.Builder()
                .code(207)
                .setHeader("Content-Type", "application/xml; charset=utf-8")
                .body("<multistatus xmlns='DAV:'></multistatus>").build()
        )
        dav.propfind(0, WebDAV.ResourceType) { _, _ ->
            fail("Shouldn't be called")
        }

        // multi-status response with <response>/<status> element indicating success
        mockServer.enqueue(
            MockResponse.Builder()
                .code(207)
                .setHeader("Content-Type", "application/xml; charset=utf-8")
                .body(
                    "<multistatus xmlns='DAV:'>" +
                            "  <response>" +
                            "    <href>/dav</href>" +
                            "    <status>HTTP/1.1 200 OK</status>" +
                            "  </response>" +
                            "</multistatus>"
                )
                .build()
        )
        called = false
        dav.propfind(0, WebDAV.ResourceType) { response, relation ->
            called = true
            assertTrue(response.isSuccess())
            assertEquals(at.bitfire.dav4jvm.okhttp.Response.HrefRelation.SELF, relation)
            assertEquals(0, response.properties.size)
        }
        assertTrue(called)

        // multi-status response with <response>/<propstat> element
        mockServer.enqueue(
            MockResponse.Builder()
                .code(207)
                .setHeader("Content-Type", "application/xml; charset=utf-8")
                .body(
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
                .build()
        )
        called = false
        dav.propfind(0, WebDAV.ResourceType, WebDAV.DisplayName) { response, relation ->
            called = true
            assertTrue(response.isSuccess())
            assertEquals(at.bitfire.dav4jvm.okhttp.Response.HrefRelation.SELF, relation)
            assertEquals("My DAV Collection", response[DisplayName::class.java]?.displayName)
        }
        assertTrue(called)

        // multi-status response for collection with several members; incomplete (not all <resourcetype>s listed)
        mockServer.enqueue(
            MockResponse.Builder()
                .code(207)
                .setHeader("Content-Type", "application/xml; charset=utf-8")
                .body(
                    "<multistatus xmlns='DAV:'>" +
                            "  <response>" +
                            "    <href>" + url.toString() + "</href>" +
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
                            "</multistatus>"
                ).build()
        )
        var nrCalled = 0
        dav.propfind(1, WebDAV.ResourceType, WebDAV.DisplayName) { response, relation ->
            when (response.href) {
                url.resolve("/dav/") -> {
                    assertTrue(response.isSuccess())
                    assertEquals(at.bitfire.dav4jvm.okhttp.Response.HrefRelation.SELF, relation)
                    assertTrue(response[ResourceType::class.java]!!.types.contains(WebDAV.Collection))
                    assertEquals("My DAV Collection", response[DisplayName::class.java]?.displayName)
                    nrCalled++
                }
                url.resolve("/dav/subcollection/") -> {
                    assertTrue(response.isSuccess())
                    assertEquals(at.bitfire.dav4jvm.okhttp.Response.HrefRelation.MEMBER, relation)
                    assertTrue(response[ResourceType::class.java]!!.types.contains(WebDAV.Collection))
                    assertEquals("A Subfolder", response[DisplayName::class.java]?.displayName)
                    nrCalled++
                }
                url.resolve("/dav/uid@host:file") -> {
                    assertTrue(response.isSuccess())
                    assertEquals(at.bitfire.dav4jvm.okhttp.Response.HrefRelation.MEMBER, relation)
                    assertEquals("Absolute path with @ and :", response[DisplayName::class.java]?.displayName)
                    nrCalled++
                }
                url.resolve("/dav/relative-uid@host.file") -> {
                    assertTrue(response.isSuccess())
                    assertEquals(at.bitfire.dav4jvm.okhttp.Response.HrefRelation.MEMBER, relation)
                    assertEquals("Relative path with @", response[DisplayName::class.java]?.displayName)
                    nrCalled++
                }
                url.resolve("/dav/relative:colon.vcf") -> {
                    assertTrue(response.isSuccess())
                    assertEquals(at.bitfire.dav4jvm.okhttp.Response.HrefRelation.MEMBER, relation)
                    assertEquals("Relative path with colon", response[DisplayName::class.java]?.displayName)
                    nrCalled++
                }
            }
        }
        assertEquals(5, nrCalled)


        /*** SPECIAL CASES ***/

        // same property is sent as 200 OK and 404 Not Found in same <response> (seen in iCloud)
        mockServer.enqueue(
            MockResponse.Builder()
                .code(207)
                .setHeader("Content-Type", "application/xml; charset=utf-8")
                .body(
                    "<multistatus xmlns='DAV:'>" +
                            "  <response>" +
                            "    <href>" + url.toString() + "</href>" +
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
                            "</multistatus>"
                ).build()
        )
        called = false
        dav.propfind(0, WebDAV.ResourceType, WebDAV.DisplayName) { response, relation ->
            called = true
            assertTrue(response.isSuccess())
            assertEquals(at.bitfire.dav4jvm.okhttp.Response.HrefRelation.SELF, relation)
            assertEquals(url.resolve("/dav/"), response.href)
            assertTrue(response[ResourceType::class.java]!!.types.contains(WebDAV.Collection))
            assertEquals("My DAV Collection", response[DisplayName::class.java]?.displayName)
        }
        assertTrue(called)

        // multi-status response with <propstat> that doesn't contain <status> (=> assume 200 OK)
        mockServer.enqueue(
            MockResponse.Builder()
                .code(207)
                .setHeader("Content-Type", "application/xml; charset=utf-8")
                .body(
                    "<multistatus xmlns='DAV:'>" +
                            "  <response>" +
                            "    <href>/dav</href>" +
                            "    <propstat>" +
                            "      <prop>" +
                            "        <displayname>Without Status</displayname>" +
                            "      </prop>" +
                            "    </propstat>" +
                            "  </response>" +
                            "</multistatus>"
                ).build()
        )
        called = false
        dav.propfind(0, WebDAV.DisplayName) { response, _ ->
            called = true
            assertEquals(200, response.propstat.first().status.code)
            assertEquals("Without Status", response[DisplayName::class.java]?.displayName)
        }
        assertTrue(called)
    }

    @Test
    fun testProppatch() {
        val url = sampleUrl()
        val dav = DavResource(httpClient, url)

        // multi-status response with <response>/<propstat> elements
        mockServer.enqueue(
            MockResponse.Builder()
                .code(207)
                .setHeader("Content-Type", "application/xml; charset=utf-8")
                .body(
                    "<multistatus xmlns='DAV:' xmlns:s='sample'>" +
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
                            "</multistatus>"
                )
                .build()
        )

        var called = false
        dav.proppatch(
            setProperties = mapOf(Pair(Property.Name("sample", "setThis"), "Some Value")),
            removeProperties = listOf(Property.Name("sample", "removeThis"))
        ) { _, hrefRelation ->
            called = true
            assertEquals(at.bitfire.dav4jvm.okhttp.Response.HrefRelation.SELF, hrefRelation)
        }
        assertTrue(called)
    }

    @Test
    fun testProppatch_createProppatchXml() {
        val xml = DavResource.Companion.createProppatchXml(
            setProperties = mapOf(Pair(Property.Name("sample", "setThis"), "Some Value")),
            removeProperties = listOf(Property.Name("sample", "removeThis"))
        )
        assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                "<d:propertyupdate xmlns:d=\"DAV:\">" +
                    "<d:set><d:prop><n1:setThis xmlns:n1=\"sample\">Some Value</n1:setThis></d:prop></d:set>" +
                    "<d:remove><d:prop><n2:removeThis xmlns:n2=\"sample\" /></d:prop></d:remove>" +
                "</d:propertyupdate>", xml)
    }

    @Test
    fun testPut() {
        val url = sampleUrl()
        val dav = DavResource(httpClient, url)

        /* POSITIVE TEST CASES */

        // no preconditions, 201 Created
        mockServer.enqueue(
            MockResponse.Builder()
                .code(HttpURLConnection.HTTP_CREATED)
                .setHeader("ETag", "W/\"Weak PUT ETag\"")
                .build()
        )
        var called = false
        dav.put(sampleText.toRequestBody("text/plain".toMediaType())) { response ->
            called = true
            val eTag = GetETag.fromResponse(response)!!
            assertEquals("Weak PUT ETag", eTag.eTag)
            assertTrue(eTag.weak)
            assertEquals(response.request.url, dav.location)
        }
        assertTrue(called)

        var rq = mockServer.takeRequest()
        assertEquals("PUT", rq.method)
        assertEquals(url, rq.url)
        assertNull(rq.headers["If-Match"])
        assertNull(rq.headers["If-None-Match"])

        // precondition: If-None-Match, 301 Moved Permanently + 204 No Content, no ETag in response
        mockServer.enqueue(
            MockResponse.Builder()
                .code(HttpURLConnection.HTTP_MOVED_PERM)
                .setHeader("Location", "/target").build()
        )
        mockServer.enqueue(
            MockResponse.Builder()
                .code(HttpURLConnection.HTTP_NO_CONTENT).build()
        )
        called = false
        dav.put(sampleText.toRequestBody("text/plain".toMediaType()), ifNoneMatch = true) { response ->
            called = true
            assertEquals(url.resolve("/target"), response.request.url)
            val eTag = GetETag.fromResponse(response)
            assertNull("Weak PUT ETag", eTag?.eTag)
            assertNull(eTag?.weak)
        }
        assertTrue(called)

        mockServer.takeRequest()
        rq = mockServer.takeRequest()
        assertEquals("PUT", rq.method)
        assertEquals("*", rq.headers["If-None-Match"])

        // precondition: If-Match, 412 Precondition Failed
        mockServer.enqueue(
            MockResponse.Builder()
                .code(HttpURLConnection.HTTP_PRECON_FAILED)
                .build()
        )
        called = false
        try {
            dav.put(sampleText.toRequestBody("text/plain".toMediaType()), "ExistingETag") {
                called = true
            }
            fail("Expected PreconditionFailedException")
        } catch(_: PreconditionFailedException) {}
        assertFalse(called)
        rq = mockServer.takeRequest()
        assertEquals("\"ExistingETag\"", rq.headers["If-Match"])
        assertNull(rq.headers["If-None-Match"])
    }

    @Test
    fun testSearch() {
        val url = sampleUrl()
        val dav = DavResource(httpClient, url)

        mockServer.enqueue(
            MockResponse.Builder()
                .code(207)
                .setHeader("Content-Type", "application/xml; charset=utf-8")
                .body(
                    "<multistatus xmlns='DAV:'>" +
                            "  <response>" +
                            "    <href>/dav</href>" +
                            "    <propstat>" +
                            "      <prop>" +
                            "        <displayname>Found something</displayname>" +
                            "      </prop>" +
                            "    </propstat>" +
                            "  </response>" +
                            "</multistatus>"
                ).build()
        )
        var called = false
        dav.search("<TEST/>") { response, hrefRelation ->
            assertEquals(at.bitfire.dav4jvm.okhttp.Response.HrefRelation.SELF, hrefRelation)
            assertEquals("Found something", response[DisplayName::class.java]?.displayName)
            called = true
        }
        assertTrue(called)

        val rq = mockServer.takeRequest()
        assertEquals("SEARCH", rq.method)
        assertEquals(url, rq.url)
        assertEquals("<TEST/>", rq.body?.utf8())
    }


    /** test helpers **/

    @Test
    fun testAssertMultiStatus_EmptyBody_NoXML() {
        val dav = DavResource(httpClient, "https://from.com".toHttpUrl())
        dav.assertMultiStatus(
            Response.Builder()
            .request(Request.Builder().url(dav.location).build())
            .protocol(Protocol.HTTP_1_1)
            .code(207).message("Multi-Status")
            .build())
    }

    @Test
    fun testAssertMultiStatus_EmptyBody_XML() {
        val dav = DavResource(httpClient, "https://from.com".toHttpUrl())
        dav.assertMultiStatus(
            Response.Builder()
            .request(Request.Builder().url(dav.location).build())
            .protocol(Protocol.HTTP_1_1)
            .code(207).message("Multi-Status")
            .addHeader("Content-Type", "text/xml")
            .build())
    }

    @Test
    fun testAssertMultiStatus_NonXML_ButContentIsXML() {
        val dav = DavResource(httpClient, "https://from.com".toHttpUrl())
        dav.assertMultiStatus(
            Response.Builder()
            .request(Request.Builder().url(dav.location).build())
            .protocol(Protocol.HTTP_1_1)
            .code(207).message("Multi-Status")
            .addHeader("Content-Type", "application/octet-stream")
            .body("<?xml version=\"1.0\"><test/>".toResponseBody())
            .build())
    }

    @Test(expected = DavException::class)
    fun testAssertMultiStatus_NonXML_ReallyNotXML() {
        val dav = DavResource(httpClient, "https://from.com".toHttpUrl())
        dav.assertMultiStatus(
            Response.Builder()
            .request(Request.Builder().url(dav.location).build())
            .protocol(Protocol.HTTP_1_1)
            .code(207).message("Multi-Status")
            .body("Some error occurred".toResponseBody("text/plain".toMediaType()))
            .build())
    }

    @Test(expected = DavException::class)
    fun testAssertMultiStatus_Not207() {
        val dav = DavResource(httpClient, "https://from.com".toHttpUrl())
        dav.assertMultiStatus(
            Response.Builder()
            .request(Request.Builder().url(dav.location).build())
            .protocol(Protocol.HTTP_1_1)
            .code(403).message("Multi-Status")
            .addHeader("Content-Type", "application/xml")
            .body("".toResponseBody())
            .build())
    }

    @Test
    fun testAssertMultiStatus_Ok_ApplicationXml() {
        val dav = DavResource(httpClient, "https://from.com".toHttpUrl())
        dav.assertMultiStatus(
            Response.Builder()
            .request(Request.Builder().url(dav.location).build())
            .protocol(Protocol.HTTP_1_1)
            .code(207).message("Multi-Status")
            .body("".toResponseBody("application/xml".toMediaType()))
            .build())
    }

    @Test
    fun testAssertMultiStatus_Ok_TextXml() {
        val dav = DavResource(httpClient, "https://from.com".toHttpUrl())
        dav.assertMultiStatus(
            Response.Builder()
            .request(Request.Builder().url(dav.location).build())
            .protocol(Protocol.HTTP_1_1)
            .code(207).message("Multi-Status")
            .body("".toResponseBody("text/xml".toMediaType()))
            .build())
    }

}
