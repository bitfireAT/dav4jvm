/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm

import at.bitfire.dav4jvm.exception.DavException
import at.bitfire.dav4jvm.exception.HttpException
import at.bitfire.dav4jvm.exception.PreconditionFailedException
import at.bitfire.dav4jvm.property.DisplayName
import at.bitfire.dav4jvm.property.GetContentType
import at.bitfire.dav4jvm.property.GetETag
import at.bitfire.dav4jvm.property.ResourceType
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
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
        mockServer.shutdown()
    }

    private fun sampleUrl() = mockServer.url("/dav/")


    @Test
    fun testOptions() {
        val url = sampleUrl()
        val dav = DavResource(httpClient, url)

        mockServer.enqueue(MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_OK)
                .setHeader("DAV", "  1,  2 ,3,hyperactive-access"))
        var called = false
        dav.options { davCapabilities, _ ->
            called = true
            assertTrue(davCapabilities.contains("1"))
            assertTrue(davCapabilities.contains("2"))
            assertTrue(davCapabilities.contains("3"))
            assertTrue(davCapabilities.contains("hyperactive-access"))
        }
        assertTrue(called)

        mockServer.enqueue(MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_OK))
        called = false
        dav.options { davCapabilities, _ ->
            called = true
            assertTrue(davCapabilities.isEmpty())
        }
        assertTrue(called)
    }

    @Test
    fun testMove() {
        val url = sampleUrl()
        val destination = url.resolve("test")!!

        /* POSITIVE TEST CASES */

        // no preconditions, 201 Created, new URL mapping at the destination
        mockServer.enqueue(MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_CREATED))
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
        assertEquals(url.encodedPath(), rq.path)
        assertEquals(destination.toString(), rq.getHeader("Destination"))
        assertNull(rq.getHeader("Overwrite"))

        // no preconditions, 204 No content, URL already mapped, overwrite
        mockServer.enqueue(MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_NO_CONTENT))
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
        assertEquals(url.encodedPath(), rq.path)
        assertEquals(destination.toString(), rq.getHeader("Destination"))
        assertEquals("F", rq.getHeader("Overwrite"))

        /* NEGATIVE TEST CASES */

        // 207 multi-status (e.g. errors on some of resources affected by
        // the MOVE prevented the operation from taking place)

        mockServer.enqueue(MockResponse()
                .setResponseCode(207))
        try {
            called = false
            DavResource(httpClient, url).let { dav ->
                dav.move(destination, false) { called = true }
                fail("Expected HttpException")
            }
        } catch(e: HttpException) {
            assertFalse(called)
        }
    }

    @Test
    fun testCopy() {
        val url = sampleUrl()
        val destination = url.resolve("test")!!

        /* POSITIVE TEST CASES */

        // no preconditions, 201 Created, resulted in the creation of a new resource
        mockServer.enqueue(MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_CREATED))
        var called = false
        DavResource(httpClient, url).let { dav ->
            dav.copy(destination, false) {
                called = true
            }
            assertTrue(called)
        }

        var rq = mockServer.takeRequest()
        assertEquals("COPY", rq.method)
        assertEquals(url.encodedPath(), rq.path)
        assertEquals(destination.toString(), rq.getHeader("Destination"))
        assertNull(rq.getHeader("Overwrite"))

        // no preconditions, 204 No content, resource successfully copied to a preexisting
        // destination resource
        mockServer.enqueue(MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_NO_CONTENT))
        called = false
        DavResource(httpClient, url).let { dav ->
            dav.copy(destination, true) {
                called = true
            }
            assertTrue(called)
        }

        rq = mockServer.takeRequest()
        assertEquals("COPY", rq.method)
        assertEquals(url.encodedPath(), rq.path)
        assertEquals(destination.toString(), rq.getHeader("Destination"))
        assertEquals("F", rq.getHeader("Overwrite"))

        /* NEGATIVE TEST CASES */

        // 207 multi-status (e.g. errors on some of resources affected by
        // the COPY prevented the operation from taking place)

        mockServer.enqueue(MockResponse()
                .setResponseCode(207))
        try {
            called = false
            DavResource(httpClient, url).let { dav ->
                dav.copy(destination, false) { called = true }
                fail("Expected HttpException")
            }
        } catch(e: HttpException) {
            assertFalse(called)
        }
    }

    @Test
    fun testGet() {
        val url = sampleUrl()
        val dav = DavResource(httpClient, url)

        /* POSITIVE TEST CASES */

        // 200 OK
        mockServer.enqueue(MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_OK)
                .setHeader("ETag", "W/\"My Weak ETag\"")
                .setHeader("Content-Type", "application/x-test-result")
                .setBody(sampleText))
        var called = false
        dav.get("*/*") { response ->
            called = true
            assertEquals(sampleText, response.body()!!.string())

            assertEquals("My Weak ETag", GetETag.fromResponse(response)?.eTag)
            assertEquals("application/x-test-result", GetContentType(response.body()!!.contentType()!!).type)
        }
        assertTrue(called)

        var rq = mockServer.takeRequest()
        assertEquals("GET", rq.method)
        assertEquals(url.encodedPath(), rq.path)
        assertEquals("*/*", rq.getHeader("Accept"))

        // 302 Moved Temporarily + 200 OK
        mockServer.enqueue(MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_MOVED_TEMP)
                .setHeader("Location", "/target")
                .setBody("This resource was moved."))
        mockServer.enqueue(MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_OK)
                .setHeader("ETag", "\"StrongETag\"")
                .setBody(sampleText))
        called = false
        dav.get("*/*") { response ->
            called = true
            assertEquals(sampleText, response.body()!!.string())
            assertEquals("StrongETag", GetETag(response.header("ETag")).eTag)
        }
        assertTrue(called)

        mockServer.takeRequest()
        rq = mockServer.takeRequest()
        assertEquals("GET", rq.method)
        assertEquals("/target", rq.path)

        // 200 OK without ETag in response
        mockServer.enqueue(MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_OK)
                .setBody(sampleText))
        called = false
        dav.get("*/*") { response ->
            called = true
            assertNull(response.header("ETag"))
        }
        assertTrue(called)
    }

    @Test
    fun testPut() {
        val url = sampleUrl()
        val dav = DavResource(httpClient, url)

        /* POSITIVE TEST CASES */

        // no preconditions, 201 Created
        mockServer.enqueue(MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_CREATED)
                .setHeader("ETag", "W/\"Weak PUT ETag\""))
        var called = false
        dav.put(RequestBody.create(MediaType.parse("text/plain"), sampleText), null, false) { response ->
            called = true
            assertEquals("Weak PUT ETag", GetETag.fromResponse(response)!!.eTag)
            assertEquals(response.request().url(), dav.location)
        }
        assertTrue(called)

        var rq = mockServer.takeRequest()
        assertEquals("PUT", rq.method)
        assertEquals(url.encodedPath(), rq.path)
        assertNull(rq.getHeader("If-Match"))
        assertNull(rq.getHeader("If-None-Match"))

        // precondition: If-None-Match, 301 Moved Permanently + 204 No Content, no ETag in response
        mockServer.enqueue(MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_MOVED_PERM)
                .setHeader("Location", "/target"))
        mockServer.enqueue(MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_NO_CONTENT))
        called = false
        dav.put(RequestBody.create(MediaType.parse("text/plain"), sampleText), null, true) { response ->
            called = true
            assertEquals(url.resolve("/target"), response.request().url())
            assertNull("Weak PUT ETag", GetETag.fromResponse(response)?.eTag)
        }
        assertTrue(called)

        mockServer.takeRequest()
        rq = mockServer.takeRequest()
        assertEquals("PUT", rq.method)
        assertEquals("*", rq.getHeader("If-None-Match"))

        // precondition: If-Match, 412 Precondition Failed
        mockServer.enqueue(MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_PRECON_FAILED))
        called = false
        try {
            dav.put(RequestBody.create(MediaType.parse("text/plain"), sampleText), "ExistingETag", false) {
                called = true
            }
            fail("Expected PreconditionFailedException")
        } catch(e: PreconditionFailedException) {}
        assertFalse(called)
        rq = mockServer.takeRequest()
        assertEquals("\"ExistingETag\"", rq.getHeader("If-Match"))
        assertNull(rq.getHeader("If-None-Match"))
    }

    @Test
    fun testDelete() {
        val url = sampleUrl()
        val dav = DavResource(httpClient, url)

        /* POSITIVE TEST CASES */

        // no preconditions, 204 No Content
        mockServer.enqueue(MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_NO_CONTENT))
        var called = false
        dav.delete(null) {
            called = true
        }
        assertTrue(called)

        var rq = mockServer.takeRequest()
        assertEquals("DELETE", rq.method)
        assertEquals(url.encodedPath(), rq.path)
        assertNull(rq.getHeader("If-Match"))

        // precondition: If-Match, 200 OK
        mockServer.enqueue(MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_OK)
                .setBody("Resource has been deleted."))
        called = false
        dav.delete("DeleteOnlyThisETag") {
            called = true
        }
        assertTrue(called)

        rq = mockServer.takeRequest()
        assertEquals("\"DeleteOnlyThisETag\"", rq.getHeader("If-Match"))

        // 302 Moved Temporarily
        mockServer.enqueue(MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_MOVED_TEMP)
                .setHeader("Location", "/new-location")
        )
        mockServer.enqueue(MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_OK))
        called = false
        dav.delete(null) {
            called = true
        }
        assertTrue(called)

        /* NEGATIVE TEST CASES */

        // 207 multi-status (e.g. single resource couldn't be deleted when DELETEing a collection)
        mockServer.enqueue(MockResponse()
                .setResponseCode(207))
        try {
            called = false
            dav.delete(null) { called = true }
            fail("Expected HttpException")
        } catch(e: HttpException) {
            assertFalse(called)
        }
    }

    @Test
    fun testPropfindAndMultiStatus() {
        val url = sampleUrl()
        val dav = DavResource(httpClient, url)

        /*** NEGATIVE TESTS ***/

        // test for non-multi-status responses:
        // * 500 Internal Server Error
        mockServer.enqueue(MockResponse().setResponseCode(HttpURLConnection.HTTP_INTERNAL_ERROR))
        var called = false
        try {
            dav.propfind(0, ResourceType.NAME) { _, _ -> called = true }
            fail("Expected HttpException")
        } catch(e: HttpException) {
            assertFalse(called)
        }
        // * 200 OK (instead of 207 Multi-Status)
        mockServer.enqueue(MockResponse().setResponseCode(HttpURLConnection.HTTP_OK))
        try {
            called = false
            dav.propfind(0, ResourceType.NAME) { _, _ -> called = true }
            fail("Expected DavException")
        } catch(e: DavException) {
            assertFalse(called)
        }

        // test for invalid multi-status responses:
        // * non-XML response
        mockServer.enqueue(MockResponse()
                .setResponseCode(207)
                .setHeader("Content-Type", "text/html")
                .setBody("<html></html>"))
        try {
            called = false
            dav.propfind(0, ResourceType.NAME) { _, _ -> called = true }
            fail("Expected DavException")
        } catch(e: DavException) {
            assertFalse(called)
        }

        // * malformed XML response
        mockServer.enqueue(MockResponse()
                .setResponseCode(207)
                .setHeader("Content-Type", "application/xml; charset=utf-8")
                .setBody("<malformed-xml>"))
        try {
            called = false
            dav.propfind(0, ResourceType.NAME) { _, _ -> called = true }
            fail("Expected DavException")
        } catch(e: DavException) {
            assertFalse(called)
        }

        // * response without <multistatus> root element
        mockServer.enqueue(MockResponse()
                .setResponseCode(207)
                .setHeader("Content-Type", "application/xml; charset=utf-8")
                .setBody("<test></test>"))
        try {
            called = false
            dav.propfind(0, ResourceType.NAME) { _, _ -> called = true }
            fail("Expected DavException")
        } catch(e: DavException) {
            assertFalse(called)
        }

        // * multi-status response with invalid <status> in <response>
        mockServer.enqueue(MockResponse()
                .setResponseCode(207)
                .setHeader("Content-Type", "application/xml; charset=utf-8")
                .setBody("<multistatus xmlns='DAV:'>" +
                         "  <response>" +
                         "    <href>/dav</href>" +
                         "    <status>Invalid Status Line</status>" +
                         "  </response>" +
                         "</multistatus>"))
        called = false
        dav.propfind(0, ResourceType.NAME) { response, relation ->
            assertEquals(Response.HrefRelation.SELF, relation)
            assertEquals(500, response.status?.code)
            called = true
        }
        assertTrue(called)

        // * multi-status response with <response>/<status> element indicating failure
        mockServer.enqueue(MockResponse()
                .setResponseCode(207)
                .setHeader("Content-Type", "application/xml; charset=utf-8")
                .setBody("<multistatus xmlns='DAV:'>" +
                         "  <response>" +
                         "    <href>/dav</href>" +
                         "    <status>HTTP/1.1 403 Forbidden</status>" +
                         "  </response>" +
                         "</multistatus>"))
        called = false
        dav.propfind(0, ResourceType.NAME) { response, relation ->
            assertEquals(Response.HrefRelation.SELF, relation)
            assertEquals(403, response.status?.code)
            called = true
        }
        assertTrue(called)

        // * multi-status response with invalid <status> in <propstat>
        mockServer.enqueue(MockResponse()
                .setResponseCode(207)
                .setHeader("Content-Type", "application/xml; charset=utf-8")
                .setBody("<multistatus xmlns='DAV:'>" +
                        "  <response>" +
                        "    <href>/dav</href>" +
                        "    <propstat>" +
                        "      <prop>" +
                        "        <resourcetype><collection/></resourcetype>" +
                        "      </prop>" +
                        "      <status>Invalid Status Line</status>" +
                        "    </propstat>" +
                        "  </response>" +
                        "</multistatus>"))
        called = false
        dav.propfind(0, ResourceType.NAME) { response, relation ->
            called = true
            assertEquals(Response.HrefRelation.SELF, relation)
            assertTrue(response.properties.filterIsInstance(ResourceType::class.java).isEmpty())
        }
        assertTrue(called)


        /*** POSITIVE TESTS ***/

        // multi-status response without <response> elements
        mockServer.enqueue(MockResponse()
                .setResponseCode(207)
                .setHeader("Content-Type", "application/xml; charset=utf-8")
                .setBody("<multistatus xmlns='DAV:'></multistatus>"))
        dav.propfind(0, ResourceType.NAME) { _, _ ->
            fail("Shouldn't be called")
        }

        // multi-status response with <response>/<status> element indicating success
        mockServer.enqueue(MockResponse()
                .setResponseCode(207)
                .setHeader("Content-Type", "application/xml; charset=utf-8")
                .setBody("<multistatus xmlns='DAV:'>" +
                        "  <response>" +
                        "    <href>/dav</href>" +
                        "    <status>HTTP/1.1 200 OK</status>" +
                        "  </response>" +
                        "</multistatus>"))
        called = false
        dav.propfind(0, ResourceType.NAME) { response, relation ->
            called = true
            assertTrue(response.isSuccess())
            assertEquals(Response.HrefRelation.SELF, relation)
            assertEquals(0, response.properties.size)
        }
        assertTrue(called)

        // multi-status response with <response>/<propstat> element
        mockServer.enqueue(MockResponse()
                .setResponseCode(207)
                .setHeader("Content-Type", "application/xml; charset=utf-8")
                .setBody("<multistatus xmlns='DAV:'>" +
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
                         "</multistatus>"))
        called = false
        dav.propfind(0, ResourceType.NAME, DisplayName.NAME) { response, relation ->
            called = true
            assertTrue(response.isSuccess())
            assertEquals(Response.HrefRelation.SELF, relation)
            assertEquals("My DAV Collection", response[DisplayName::class.java]?.displayName)
        }
        assertTrue(called)

        // multi-status response for collection with several members; incomplete (not all <resourcetype>s listed)
        mockServer.enqueue(MockResponse()
                .setResponseCode(207)
                .setHeader("Content-Type", "application/xml; charset=utf-8")
                .setBody("<multistatus xmlns='DAV:'>" +
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
                        "</multistatus>"))
        var nrCalled = 0
        dav.propfind(1, ResourceType.NAME, DisplayName.NAME) { response, relation ->
            when (response.href) {
                url.resolve("/dav/") -> {
                    assertTrue(response.isSuccess())
                    assertEquals(Response.HrefRelation.SELF, relation)
                    assertTrue(response[ResourceType::class.java]!!.types.contains(ResourceType.COLLECTION))
                    assertEquals("My DAV Collection", response[DisplayName::class.java]?.displayName)
                    nrCalled++
                }
                url.resolve("/dav/subcollection/") -> {
                    assertTrue(response.isSuccess())
                    assertEquals(Response.HrefRelation.MEMBER, relation)
                    assertTrue(response[ResourceType::class.java]!!.types.contains(ResourceType.COLLECTION))
                    assertEquals("A Subfolder", response[DisplayName::class.java]?.displayName)
                    nrCalled++
                }
                url.resolve("/dav/uid@host:file") -> {
                    assertTrue(response.isSuccess())
                    assertEquals(Response.HrefRelation.MEMBER, relation)
                    assertEquals("Absolute path with @ and :", response[DisplayName::class.java]?.displayName)
                    nrCalled++
                }
                url.resolve("/dav/relative-uid@host.file") -> {
                    assertTrue(response.isSuccess())
                    assertEquals(Response.HrefRelation.MEMBER, relation)
                    assertEquals("Relative path with @", response[DisplayName::class.java]?.displayName)
                    nrCalled++
                }
                url.resolve("/dav/relative:colon.vcf") -> {
                    assertTrue(response.isSuccess())
                    assertEquals(Response.HrefRelation.MEMBER, relation)
                    assertEquals("Relative path with colon", response[DisplayName::class.java]?.displayName)
                    nrCalled++
                }
            }
        }
        assertEquals(4, nrCalled)


        /*** SPECIAL CASES ***/

        // same property is sent as 200 OK and 404 Not Found in same <response> (seen in iCloud)
        mockServer.enqueue(MockResponse()
                .setResponseCode(207)
                .setHeader("Content-Type", "application/xml; charset=utf-8")
                .setBody("<multistatus xmlns='DAV:'>" +
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
                        "</multistatus>"))
        called = false
        dav.propfind(0, ResourceType.NAME, DisplayName.NAME) { response, relation ->
            called = true
            assertTrue(response.isSuccess())
            assertEquals(Response.HrefRelation.SELF, relation)
            assertEquals(url.resolve("/dav/"), response.href)
            assertTrue(response[ResourceType::class.java]!!.types.contains(ResourceType.COLLECTION))
            assertEquals("My DAV Collection", response[DisplayName::class.java]?.displayName)
        }
        assertTrue(called)

        // multi-status response with <propstat> that doesn't contain <status> (=> assume 200 OK)
        mockServer.enqueue(MockResponse()
                .setResponseCode(207)
                .setHeader("Content-Type", "application/xml; charset=utf-8")
                .setBody("<multistatus xmlns='DAV:'>" +
                        "  <response>" +
                        "    <href>/dav</href>" +
                        "    <propstat>" +
                        "      <prop>" +
                        "        <displayname>Without Status</displayname>" +
                        "      </prop>" +
                        "    </propstat>" +
                        "  </response>" +
                        "</multistatus>"))
        called = false
        dav.propfind(0, DisplayName.NAME) { response, _ ->
            called = true
            assertEquals(200, response.propstat.first().status.code)
            assertEquals("Without Status", response[DisplayName::class.java]?.displayName)
        }
        assertTrue(called)
    }

}
