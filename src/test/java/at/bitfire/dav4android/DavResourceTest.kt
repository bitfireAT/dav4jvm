/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4android

import at.bitfire.dav4android.exception.DavException
import at.bitfire.dav4android.exception.HttpException
import at.bitfire.dav4android.exception.PreconditionFailedException
import at.bitfire.dav4android.property.DisplayName
import at.bitfire.dav4android.property.GetContentType
import at.bitfire.dav4android.property.GetETag
import at.bitfire.dav4android.property.ResourceType
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
        val response = dav.options()
        assertTrue(response.capabilities.contains("1"))
        assertTrue(response.capabilities.contains("2"))
        assertTrue(response.capabilities.contains("3"))
        assertTrue(response.capabilities.contains("hyperactive-access"))

        mockServer.enqueue(MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_OK))
        assertTrue(dav.options().capabilities.isEmpty())
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
        dav.get("*/*").use { responseOK ->
            assertEquals(sampleText, responseOK.body!!.string())
            assertEquals("My Weak ETag", responseOK[GetETag::class.java]?.eTag)
            assertEquals("application/x-test-result", responseOK[GetContentType::class.java]?.type)
        }

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
        dav.get("*/*").use { response302 ->
            assertEquals(sampleText, response302.body!!.string())
            assertEquals("StrongETag", response302[GetETag::class.java]?.eTag)
        }

        mockServer.takeRequest()
        rq = mockServer.takeRequest()
        assertEquals("GET", rq.method)
        assertEquals("/target", rq.path)

        // 200 OK without ETag in response
        mockServer.enqueue(MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_OK)
                .setBody(sampleText))
        dav.get("*/*").use {
            assertNull(it[GetETag::class.java])
        }
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
        dav.put(RequestBody.create(MediaType.parse("text/plain"), sampleText), null, false).use {
            assertEquals("Weak PUT ETag", it[GetETag::class.java]?.eTag)
            assertEquals(it.url, dav.location)
        }

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
        dav.put(RequestBody.create(MediaType.parse("text/plain"), sampleText), null, true).use {
            assertEquals(url.resolve("/target"), it.url)
            assertNull("Weak PUT ETag", it[GetETag::class.java]?.eTag)
        }

        mockServer.takeRequest()
        rq = mockServer.takeRequest()
        assertEquals("PUT", rq.method)
        assertEquals("*", rq.getHeader("If-None-Match"))

        // precondition: If-Match, 412 Precondition Failed
        mockServer.enqueue(MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_PRECON_FAILED))
        try {
            dav.put(RequestBody.create(MediaType.parse("text/plain"), sampleText), "ExistingETag", false).close()
            fail("Expected PreconditionFailedException")
        } catch(e: PreconditionFailedException) {}
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
        dav.delete(null).close()

        var rq = mockServer.takeRequest()
        assertEquals("DELETE", rq.method)
        assertEquals(url.encodedPath(), rq.path)
        assertNull(rq.getHeader("If-Match"))

        // precondition: If-Match, 200 OK
        mockServer.enqueue(MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_OK)
                .setBody("Resource has been deleted."))
        dav.delete("DeleteOnlyThisETag").close()

        rq = mockServer.takeRequest()
        assertEquals("\"DeleteOnlyThisETag\"", rq.getHeader("If-Match"))

        // 302 Moved Temporarily
        mockServer.enqueue(MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_MOVED_TEMP)
                .setHeader("Location", "/new-location")
        )
        mockServer.enqueue(MockResponse()
                .setResponseCode(HttpURLConnection.HTTP_OK))
        dav.delete(null).close()

        /* NEGATIVE TEST CASES */

        // 207 multi-status (e.g. single resource couldn't be deleted when DELETEing a collection)
        mockServer.enqueue(MockResponse()
                .setResponseCode(207))
        try {
            dav.delete(null).close()
            fail("Expected HttpException")
        } catch(e: HttpException) {}
    }

    @Test
    fun testPropfindAndMultiStatus() {
        val url = sampleUrl()
        val dav = DavResource(httpClient, url)

        /*** NEGATIVE TESTS ***/

        // test for non-multi-status responses:
        // * 500 Internal Server Error
        mockServer.enqueue(MockResponse().setResponseCode(HttpURLConnection.HTTP_INTERNAL_ERROR))
        try {
            dav.propfind(0, ResourceType.NAME).close()
            fail("Expected HttpException")
        } catch(e: HttpException) {}
        // * 200 OK (instead of 207 Multi-Status)
        mockServer.enqueue(MockResponse().setResponseCode(HttpURLConnection.HTTP_OK))
        try {
            dav.propfind(0, ResourceType.NAME).close()
            fail("Expected DavException")
        } catch(e: DavException) {}

        // test for invalid multi-status responses:
        // * non-XML response
        mockServer.enqueue(MockResponse()
                .setResponseCode(207)
                .setHeader("Content-Type", "text/html")
                .setBody("<html></html>"))
        try {
            dav.propfind(0, ResourceType.NAME).close()
            fail("Expected DavException")
        } catch(e: DavException) {}

        // * malformed XML response
        mockServer.enqueue(MockResponse()
                .setResponseCode(207)
                .setHeader("Content-Type", "application/xml; charset=utf-8")
                .setBody("<malformed-xml>"))
        try {
            dav.propfind(0, ResourceType.NAME).close()
            fail("Expected DavException")
        } catch(e: DavException) {}

        // * response without <multistatus> root element
        mockServer.enqueue(MockResponse()
                .setResponseCode(207)
                .setHeader("Content-Type", "application/xml; charset=utf-8")
                .setBody("<test></test>"))
        try {
            dav.propfind(0, ResourceType.NAME).close()
            fail("Expected DavException")
        } catch(e: DavException) {}

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
        try {
            dav.propfind(0, ResourceType.NAME).close()
            fail("Expected HttpException")
        } catch(e: HttpException) {}

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
        try {
            dav.propfind(0, ResourceType.NAME)
            fail("Expected HttpException")
        } catch(e: HttpException) {}

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
        dav.propfind(0, ResourceType.NAME).use {
            assertNull(it[ResourceType::class.java])
        }


        /*** POSITIVE TESTS ***/

        // multi-status response without <response> elements
        mockServer.enqueue(MockResponse()
                .setResponseCode(207)
                .setHeader("Content-Type", "application/xml; charset=utf-8")
                .setBody("<multistatus xmlns='DAV:'></multistatus>"))
        dav.propfind(0, ResourceType.NAME).use {
            assertEquals(0, it.properties.size)
            assertEquals(0, it.members.size)
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
        dav.propfind(0, ResourceType.NAME).use {
            assertEquals(0, it.properties.size)
            assertEquals(0, it.members.size)
        }

        // multi-status response with <response>/<propstat> element
        mockServer.enqueue(MockResponse()
                .setResponseCode(207)
                .setHeader("Content-Type", "application/xml; charset=utf-8")
                .setBody("<multistatus xmlns='DAV:'>" +
                         "  <response>" +
                         "    <href>/dav</href>" +
                         "    <propstat>" +
                         "      <prop>" +
                         "        <resourcetype>" +
                         "        </resourcetype>" +
                         "        <displayname>My DAV Collection</displayname>" +
                         "      </prop>" +
                         "      <status>HTTP/1.1 200 OK</status>" +
                         "    </propstat>" +
                         "  </response>" +
                         "</multistatus>"))
        dav.propfind(0, ResourceType.NAME, DisplayName.NAME).use {
            assertEquals("My DAV Collection", it[DisplayName::class.java]?.displayName)
            assertEquals(0, it.members.size)
        }

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
        dav.propfind(1, ResourceType.NAME, DisplayName.NAME).use { response ->
            assertEquals(4, response.members.size)
            val ok = BooleanArray(4)
            for (member in response.members)
                when (member.url) {
                    url.resolve("/dav/subcollection/") -> {
                        assertTrue(member[ResourceType::class.java]!!.types.contains(ResourceType.COLLECTION))
                        assertEquals("A Subfolder", member[DisplayName::class.java]?.displayName)
                        ok[0] = true
                    }
                    url.resolve("/dav/uid@host:file") -> {
                        assertEquals("Absolute path with @ and :", member[DisplayName::class.java]?.displayName)
                        ok[1] = true
                    }
                    url.resolve("/dav/relative-uid@host.file") -> {
                        assertEquals("Relative path with @", member[DisplayName::class.java]?.displayName)
                        ok[2] = true
                    }
                    url.resolve("/dav/relative:colon.vcf") -> {
                        assertEquals("Relative path with colon", member[DisplayName::class.java]?.displayName)
                        ok[3] = true
                    }
                }
            assertTrue(ok.all { it })
        }

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
        dav.propfind(0, ResourceType.NAME, DisplayName.NAME).use {
            assertTrue(it[ResourceType::class.java]!!.types.contains(ResourceType.COLLECTION))
            assertEquals("My DAV Collection", it[DisplayName::class.java]?.displayName)
        }

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
        dav.propfind(0, DisplayName.NAME).use {
            assertEquals("Without Status", it[DisplayName::class.java]?.displayName)
        }
    }

}
