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
import at.bitfire.dav4jvm.property.GetETag
import at.bitfire.dav4jvm.property.ResourceType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.*
import io.ktor.utils.io.charsets.*
import nl.adaptivity.xmlutil.QName
import kotlin.test.*

@OptIn(InternalAPI::class)
object DavResourceTest : FunSpec({

    val sampleText = "SAMPLE RESPONSE"

    val sampleUrl = Url("http://mock-server.com/dav/")

    val httpClient = createMockClient()


    test("testCopy") {
        val url = sampleUrl
        val destination = url.resolve("test")

        /* POSITIVE TEST CASES */

        // no preconditions, 201 Created, resulted in the creation of a new resource
        httpClient.changeMockHandler { request ->
            if (request.url == sampleUrl) {
                respond("", HttpStatusCode.Created)
            } else {
                respondError(HttpStatusCode.BadRequest)
            }
        }
        var called = false
        DavResource(httpClient, url).let { dav ->
            dav.copy(destination, false) {
                called = true
            }
            assertTrue(called)
        }

        var rq = httpClient.lastMockRequest
        assertEquals(DavResource.Copy, rq.method)
        assertEquals(url.encodedPath, rq.url.encodedPath)
        assertEquals(destination.toString(), rq.headers["Destination"])
        assertNull(rq.headers["Overwrite"])

        // no preconditions, 204 No content, resource successfully copied to a preexisting
        // destination resource
        httpClient.changeMockHandler { request ->
            if (request.url == sampleUrl) {
                respond("", HttpStatusCode.NoContent)
            } else {
                respondError(HttpStatusCode.BadRequest)
            }
        }
        called = false
        DavResource(httpClient, url).let { dav ->
            dav.copy(destination, true) {
                called = true
            }
            assertTrue(called)
        }

        rq = httpClient.lastMockRequest
        assertEquals(DavResource.Copy, rq.method)
        assertEquals(url.encodedPath, rq.url.encodedPath)
        assertEquals(destination.toString(), rq.headers["Destination"])
        assertEquals("F", rq.headers["Overwrite"])

        /* NEGATIVE TEST CASES */

        // 207 multi-status (e.g. errors on some of resources affected by
        // the COPY prevented the operation from taking place)

        httpClient.changeMockHandler { request ->
            if (request.url == sampleUrl) {
                respond("", HttpStatusCode.MultiStatus)
            } else {
                respondError(HttpStatusCode.BadRequest)
            }
        }
        try {
            called = false
            DavResource(httpClient, url).let { dav ->
                dav.copy(destination, false) { called = true }
                fail("Expected HttpException")
            }
        } catch (e: HttpException) {
            assertFalse(called)
        }
    }

    test("testDelete") {
        val url = sampleUrl

        val dav = DavResource(httpClient, url)

        /* POSITIVE TEST CASES */

        // no preconditions, 204 No Content
        httpClient.changeMockHandler { request ->
            if (request.url == sampleUrl) {
                respond("", HttpStatusCode.NoContent)
            } else {
                respondError(HttpStatusCode.BadRequest)
            }
        }
        var called = false
        dav.delete {
            called = true
        }
        assertTrue(called)

        var rq = httpClient.lastMockRequest
        assertEquals(HttpMethod.Delete, rq.method)
        assertEquals(url.encodedPath, rq.url.encodedPath)
        assertNull(rq.headers["If-Match"])

        // precondition: If-Match / If-Schedule-Tag-Match, 200 OK
        httpClient.changeMockHandler { request ->
            if (request.url == sampleUrl) {
                respondOk("Resource has been deleted.")
            } else {
                respondError(HttpStatusCode.BadRequest)
            }
        }
        called = false
        dav.delete("DeleteOnlyThisETag", "DeleteOnlyThisScheduleTag") {
            called = true
        }
        assertTrue(called)

        rq = httpClient.lastMockRequest
        assertEquals("\"DeleteOnlyThisETag\"", rq.headers["If-Match"])
        assertEquals("\"DeleteOnlyThisScheduleTag\"", rq.headers["If-Schedule-Tag-Match"])

        // 302 Moved Temporarily
        httpClient.changeMockHandler { request ->
            if (request.url == sampleUrl) {
                respond("", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "/new-location"))
            } else if (request.url == sampleUrl.resolve("/new-location")) {
                respondOk("")
            } else {
                respondError(HttpStatusCode.BadRequest)
            }
        }
        called = false
        dav.delete(null) {
            called = true
        }
        assertTrue(called)

        /* NEGATIVE TEST CASES */

        // 207 multi-status (e.g. single resource couldn't be deleted when DELETEing a collection)
        httpClient.changeMockHandler { request ->
            if (request.url == sampleUrl.resolve("/new-location")) {
                respond("", HttpStatusCode.MultiStatus)
            } else {
                respondError(HttpStatusCode.BadRequest)
            }
        }
        try {
            called = false
            dav.delete(null) { called = true }
            fail("Expected HttpException")
        } catch (e: HttpException) {
            assertFalse(called)
        }
    }

    //TODO
    /*
    @Test
    fun testFollowRedirects_302() {
        val url = sampleUrl
        val dav = DavResource(httpClient, url)
        var i = 0
        dav.followRedirects {
            if (i++ == 0)
                okhttp3.Response.Builder()
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
                okhttp3.Response.Builder()
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
            okhttp3.Response.Builder()
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
     */

    test("testGet") {
        val url = sampleUrl
        val mContentType = ContentType("application", "x-test-result")

        val dav = DavResource(httpClient, url)

        /* POSITIVE TEST CASES */

        // 200 OK
        httpClient.changeMockHandler { request ->
            if (request.url == sampleUrl) {
                respond(
                    sampleText,
                    HttpStatusCode.OK,
                    headersOf(
                        HttpHeaders.ETag to listOf("W/\"My Weak ETag\""),
                        HttpHeaders.ContentType to listOf(mContentType.toString())
                    )
                )
            } else {
                respondError(HttpStatusCode.BadRequest)
            }
        }

        var called = false
        dav.get("*/*", null) { response ->
            called = true
            assertEquals(sampleText, response.bodyAsText())

            val eTag = GetETag.fromResponse(response)
            assertEquals("My Weak ETag", eTag!!.eTag)
            assertTrue(eTag.weak)
            assertEquals(
                mContentType,
                response.contentType()
            )
        }
        assertTrue(called)

        var rq = httpClient.lastMockRequest
        assertEquals(HttpMethod.Get, rq.method)
        assertEquals(url.encodedPath, rq.url.encodedPath)
        assertEquals("*/*", rq.headers["Accept"])

        // 302 Moved Temporarily + 200 OK
        httpClient.changeMockHandler { request ->
            if (request.url == sampleUrl) {
                respond(
                    "This resource was moved",
                    HttpStatusCode.Found,
                    headersOf(
                        HttpHeaders.Location to listOf("/target")
                    )
                )
            } else if (request.url == sampleUrl.resolve("/target")) {
                respond(sampleText, HttpStatusCode.OK, headersOf(HttpHeaders.ETag, "\"StrongETag\""))
            } else {
                respondError(HttpStatusCode.BadRequest)
            }
        }
        called = false
        dav.get("*/*", null) { response ->
            called = true
            assertEquals(sampleText, response.bodyAsText())
            val eTag = GetETag(response.headers["ETag"]!!)
            assertEquals("StrongETag", eTag.eTag)
            assertFalse(eTag.weak)
        }
        assertTrue(called)

        httpClient.lastMockRequest
        rq = httpClient.lastMockRequest
        assertEquals(HttpMethod.Get, rq.method)
        assertEquals("/target", rq.url.fullPath)

        // 200 OK without ETag in response
        httpClient.changeMockHandler { request ->
            if (request.url == sampleUrl.resolve("/target")) {
                respond(
                    sampleText,
                    HttpStatusCode.OK
                )
            } else {
                respondError(HttpStatusCode.BadRequest)
            }
        }
        called = false
        dav.get("*/*", null) { response ->
            called = true
            assertNull(response.headers["ETag"])
        }
        assertTrue(called)
    }

    test("testGetRange_Ok") {
        val url = sampleUrl
        val dav = DavResource(httpClient, url)

        httpClient.changeMockHandler { request ->
            if (request.url == sampleUrl) {
                respond(
                    "",
                    HttpStatusCode.PartialContent
                )
            } else {
                respondError(HttpStatusCode.BadRequest)
            }
        }

        var called = false
        dav.getRange("*/*", 100, 342) { response ->
            assertEquals("bytes=100-441", response.request.headers["Range"])
            called = true
        }
        assertTrue(called)
    }

    test("testMove") {
        val url = sampleUrl
        val destination = url.resolve("test")

        /* POSITIVE TEST CASES */

        // no preconditions, 201 Created, new URL mapping at the destination
        httpClient.changeMockHandler { request ->
            if (request.url == sampleUrl) {
                respond(
                    "",
                    HttpStatusCode.Created
                )
            } else {
                respondError(HttpStatusCode.BadRequest)
            }
        }
        var called = false
        DavResource(httpClient, url).let { dav ->
            dav.move(destination, false) {
                called = true
            }
            assertTrue(called)
            assertEquals(destination, dav.location)
        }

        var rq = httpClient.lastMockRequest
        assertEquals(DavResource.Move, rq.method)
        assertEquals(url.encodedPath, rq.url.encodedPath)
        assertEquals(destination.toString(), rq.headers["Destination"])
        assertNull(rq.headers["Overwrite"])

        // no preconditions, 204 No content, URL already mapped, overwrite
        httpClient.changeMockHandler { request ->
            if (request.url == sampleUrl) {
                respond(
                    "",
                    HttpStatusCode.NoContent
                )
            } else {
                respondError(HttpStatusCode.BadRequest)
            }
        }
        called = false
        DavResource(httpClient, url).let { dav ->
            dav.move(destination, true) {
                called = true
            }
            assertTrue(called)
            assertEquals(destination, dav.location)
        }

        rq = httpClient.lastMockRequest
        assertEquals(DavResource.Move, rq.method)
        assertEquals(url.encodedPath, rq.url.encodedPath)
        assertEquals(destination.toString(), rq.headers["Destination"])
        assertEquals("F", rq.headers["Overwrite"])

        /* NEGATIVE TEST CASES */

        // 207 multi-status (e.g. errors on some of resources affected by
        // the MOVE prevented the operation from taking place)
        httpClient.changeMockHandler { request ->
            if (request.url == sampleUrl) {
                respond(
                    "",
                    HttpStatusCode.MultiStatus
                )
            } else {
                respondError(HttpStatusCode.BadRequest)
            }
        }
        try {
            called = false
            DavResource(httpClient, url).let { dav ->
                dav.move(destination, false) { called = true }
                fail("Expected HttpException")
            }
        } catch (e: HttpException) {
            assertFalse(called)
        }
    }

    test("testOptions") {
        val url = sampleUrl
        val dav = DavResource(httpClient, url)

        httpClient.changeMockHandler { request ->
            if (request.url == sampleUrl) {
                respond(
                    "",
                    HttpStatusCode.OK,
                    headersOf("DAV", listOf("  1", "  2 ", "3", "hyperactive-access"))
                )
            } else {
                respondError(HttpStatusCode.BadRequest)
            }
        }

        var called = false
        dav.options { davCapabilities, _ ->
            called = true
            assertTrue(davCapabilities.contains("1"))
            assertTrue(davCapabilities.contains("2"))
            assertTrue(davCapabilities.contains("3"))
            assertTrue(davCapabilities.contains("hyperactive-access"))
        }
        assertTrue(called)

        httpClient.changeMockHandler { request ->
            if (request.url == sampleUrl) {
                respond(
                    "",
                    HttpStatusCode.OK
                )
            } else {
                respondError(HttpStatusCode.BadRequest)
            }
        }
        called = false
        dav.options { davCapabilities, _ ->
            called = true
            assertTrue(davCapabilities.isEmpty())
        }
        assertTrue(called)
    }

    test("testPropfindAndMultiStatus") {
        val url = sampleUrl
        val dav = DavResource(httpClient, url)

        /*** NEGATIVE TESTS ***/

        // test for non-multi-status responses:
        // * 500 Internal Server Error
        httpClient.changeMockHandler { request ->
            if (request.url == sampleUrl) {
                respondError(HttpStatusCode.InternalServerError)
            } else {
                respondError(HttpStatusCode.BadRequest)
            }
        }
        var called = false
        try {
            dav.propfind(0, ResourceType.NAME) { _, _ -> called = true }
            fail("Expected HttpException")
        } catch (e: HttpException) {
            assertFalse(called)
        }
        // * 200 OK (instead of 207 Multi-Status)
        httpClient.changeMockHandler { request ->
            if (request.url == sampleUrl) {
                respond(
                    "",
                    HttpStatusCode.OK
                )
            } else {
                respondError(HttpStatusCode.BadRequest)
            }
        }
        try {
            called = false
            dav.propfind(0, ResourceType.NAME) { _, _ -> called = true }
            fail("Expected DavException")
        } catch (e: DavException) {
            assertFalse(called)
        }

        // test for invalid multi-status responses:
        // * non-XML response
        httpClient.changeMockHandler { request ->
            if (request.url == sampleUrl) {
                respond(
                    "<html></html>",
                    HttpStatusCode.MultiStatus,
                    headersOf(HttpHeaders.ContentType, ContentType.Text.Html.toString())
                )
            } else {
                respondError(HttpStatusCode.BadRequest)
            }
        }
        try {
            called = false
            dav.propfind(0, ResourceType.NAME) { _, _ -> called = true }
            fail("Expected DavException")
        } catch (e: DavException) {
            assertFalse(called)
        }

        // * malformed XML response
        httpClient.changeMockHandler { request ->
            if (request.url == sampleUrl) {
                respond(
                    "<malformed-xml>",
                    HttpStatusCode.MultiStatus,
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
            called = false
            dav.propfind(0, ResourceType.NAME) { _, _ -> called = true }
            fail("Expected DavException")
        } catch (e: DavException) {
            assertFalse(called)
        }

        // * response without <multistatus> root element
        httpClient.changeMockHandler { request ->
            if (request.url == sampleUrl) {
                respond(
                    "<test></test>",
                    HttpStatusCode.MultiStatus,
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
            called = false
            dav.propfind(0, ResourceType.NAME) { _, _ -> called = true }
            fail("Expected DavException")
        } catch (e: DavException) {
            assertFalse(called)
        }

        // * multi-status response with invalid <status> in <response>
        httpClient.changeMockHandler { request ->
            if (request.url == sampleUrl) {
                respond(
                    "<multistatus xmlns='DAV:'>" +
                            "  <response>" +
                            "    <href>/dav</href>" +
                            "    <status>Invalid Status Line</status>" +
                            "  </response>" +
                            "</multistatus>",
                    HttpStatusCode.MultiStatus,
                    headersOf(
                        HttpHeaders.ContentType,
                        ContentType.Application.Xml.withCharset(Charsets.UTF_8).toString()
                    )
                )
            } else {
                respondError(HttpStatusCode.BadRequest)
            }
        }
        called = false
        dav.propfind(0, ResourceType.NAME) { response, relation ->
            assertEquals(Response.HrefRelation.SELF, relation)
            assertEquals(HttpStatusCode.InternalServerError, response.status?.status)
            called = true
        }
        assertTrue(called)

        // * multi-status response with <response>/<status> element indicating failure
        httpClient.changeMockHandler { request ->
            if (request.url == sampleUrl) {
                respond(
                    "<multistatus xmlns='DAV:'>" +
                            "  <response>" +
                            "    <href>/dav</href>" +
                            "    <status>HTTP/1.1 403 Forbidden</status>" +
                            "  </response>" +
                            "</multistatus>",
                    HttpStatusCode.MultiStatus,
                    headersOf(
                        HttpHeaders.ContentType,
                        ContentType.Application.Xml.withCharset(Charsets.UTF_8).toString()
                    )
                )
            } else {
                respondError(HttpStatusCode.BadRequest)
            }
        }
        called = false
        dav.propfind(0, ResourceType.NAME) { response, relation ->
            assertEquals(Response.HrefRelation.SELF, relation)
            assertEquals(HttpStatusCode.Forbidden, response.status?.status)
            called = true
        }
        assertTrue(called)

        // * multi-status response with invalid <status> in <propstat>
        httpClient.changeMockHandler { request ->
            if (request.url == sampleUrl) {
                respond(
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
                            "</multistatus>",
                    HttpStatusCode.MultiStatus,
                    headersOf(
                        HttpHeaders.ContentType,
                        ContentType.Application.Xml.withCharset(Charsets.UTF_8).toString()
                    )
                )
            } else {
                respondError(HttpStatusCode.BadRequest)
            }
        }
        called = false
        dav.propfind(0, ResourceType.NAME) { response, relation ->
            called = true
            assertEquals(Response.HrefRelation.SELF, relation)
            assertTrue(response.properties.filterIsInstance<ResourceType>().isEmpty())
        }
        assertTrue(called)


        /*** POSITIVE TESTS ***/

        // multi-status response without <response> elements
        httpClient.changeMockHandler { request ->
            if (request.url == sampleUrl) {
                respond(
                    "<multistatus xmlns='DAV:'></multistatus>",
                    HttpStatusCode.MultiStatus,
                    headersOf(
                        HttpHeaders.ContentType,
                        ContentType.Application.Xml.withCharset(Charsets.UTF_8).toString()
                    )
                )
            } else {
                respondError(HttpStatusCode.BadRequest)
            }
        }
        dav.propfind(0, ResourceType.NAME) { _, _ ->
            fail("Shouldn't be called")
        }

        // multi-status response with <response>/<status> element indicating success
        httpClient.changeMockHandler { request ->
            if (request.url == sampleUrl) {
                respond(
                    "<multistatus xmlns='DAV:'>" +
                            "  <response>" +
                            "    <href>/dav</href>" +
                            "    <status>HTTP/1.1 200 OK</status>" +
                            "  </response>" +
                            "</multistatus>",
                    HttpStatusCode.MultiStatus,
                    headersOf(
                        HttpHeaders.ContentType,
                        ContentType.Application.Xml.withCharset(Charsets.UTF_8).toString()
                    )
                )
            } else {
                respondError(HttpStatusCode.BadRequest)
            }
        }
        called = false
        dav.propfind(0, ResourceType.NAME) { response, relation ->
            called = true
            assertTrue(response.isSuccess())
            assertEquals(Response.HrefRelation.SELF, relation)
            assertEquals(0, response.properties.size)
        }
        assertTrue(called)

        // multi-status response with <response>/<propstat> element
        httpClient.changeMockHandler { request ->
            if (request.url == sampleUrl) {
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
                    headersOf(
                        HttpHeaders.ContentType,
                        ContentType.Application.Xml.withCharset(Charsets.UTF_8).toString()
                    )
                )
            } else {
                respondError(HttpStatusCode.BadRequest)
            }
        }
        called = false
        dav.propfind(0, ResourceType.NAME, DisplayName.NAME) { response, relation ->
            called = true
            assertTrue(response.isSuccess())
            assertEquals(Response.HrefRelation.SELF, relation)
            assertEquals("My DAV Collection", response[DisplayName::class]?.displayName)
        }
        assertTrue(called)

        // multi-status response for collection with several members; incomplete (not all <resourcetype>s listed)
        httpClient.changeMockHandler { request ->
            if (request.url == sampleUrl) {
                respond(
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
                            "</multistatus>",
                    HttpStatusCode.MultiStatus,
                    headersOf(
                        HttpHeaders.ContentType,
                        ContentType.Application.Xml.withCharset(Charsets.UTF_8).toString()
                    )
                )
            } else {
                respondError(HttpStatusCode.BadRequest)
            }
        }
        var nrCalled = 0
        dav.propfind(1, ResourceType.NAME, DisplayName.NAME) { response, relation ->
            when (response.href) {
                url.resolve("/dav/") -> {
                    assertTrue(response.isSuccess())
                    assertEquals(Response.HrefRelation.SELF, relation)
                    assertTrue(response[ResourceType::class]!!.types.contains(ResourceType.COLLECTION))
                    assertEquals("My DAV Collection", response[DisplayName::class]?.displayName)
                    nrCalled++
                }

                url.resolve("/dav/subcollection/") -> {
                    assertTrue(response.isSuccess())
                    assertEquals(Response.HrefRelation.MEMBER, relation)
                    assertTrue(response[ResourceType::class]!!.types.contains(ResourceType.COLLECTION))
                    assertEquals("A Subfolder", response[DisplayName::class]?.displayName)
                    nrCalled++
                }

                url.resolve("/dav/uid@host:file") -> {
                    assertTrue(response.isSuccess())
                    assertEquals(Response.HrefRelation.MEMBER, relation)
                    assertEquals("Absolute path with @ and :", response[DisplayName::class]?.displayName)
                    nrCalled++
                }

                url.resolve("/dav/relative-uid@host.file") -> {
                    assertTrue(response.isSuccess())
                    assertEquals(Response.HrefRelation.MEMBER, relation)
                    assertEquals("Relative path with @", response[DisplayName::class]?.displayName)
                    nrCalled++
                }

                url.resolve("/dav/relative:colon.vcf") -> {
                    assertTrue(response.isSuccess())
                    assertEquals(Response.HrefRelation.MEMBER, relation)
                    assertEquals("Relative path with colon", response[DisplayName::class]?.displayName)
                    nrCalled++
                }
            }
        }
        assertEquals(4, nrCalled)


        /*** SPECIAL CASES ***/

        // same property is sent as 200 OK and 404 Not Found in same <response> (seen in iCloud)
        httpClient.changeMockHandler { request ->
            if (request.url == sampleUrl) {
                respond(
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
                            "</multistatus>",
                    HttpStatusCode.MultiStatus,
                    headersOf(
                        HttpHeaders.ContentType,
                        ContentType.Application.Xml.withCharset(Charsets.UTF_8).toString()
                    )
                )
            } else {
                respondError(HttpStatusCode.BadRequest)
            }
        }
        called = false
        dav.propfind(0, ResourceType.NAME, DisplayName.NAME) { response, relation ->
            called = true
            assertTrue(response.isSuccess())
            assertEquals(Response.HrefRelation.SELF, relation)
            assertEquals(url.resolve("/dav/"), response.href)
            assertTrue(response[ResourceType::class]!!.types.contains(ResourceType.COLLECTION))
            assertEquals("My DAV Collection", response[DisplayName::class]?.displayName)
        }
        assertTrue(called)

        // multi-status response with <propstat> that doesn't contain <status> (=> assume 200 OK)
        httpClient.changeMockHandler { request ->
            if (request.url == sampleUrl) {
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
                    headersOf(
                        HttpHeaders.ContentType,
                        ContentType.Application.Xml.withCharset(Charsets.UTF_8).toString()
                    )
                )
            } else {
                respondError(HttpStatusCode.BadRequest)
            }
        }
        called = false
        dav.propfind(0, DisplayName.NAME) { response, _ ->
            called = true
            assertEquals(HttpStatusCode.OK, response.propstat.first().status)
            assertEquals("Without Status", response[DisplayName::class]?.displayName)
        }
        assertTrue(called)
    }

    test("testProppatch") {
        val url = sampleUrl
        val dav = DavResource(httpClient, url)

        // multi-status response with <response>/<propstat> elements
        httpClient.changeMockHandler { request ->
            if (request.url == sampleUrl) {
                respond(
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
                            "</multistatus>",
                    HttpStatusCode.MultiStatus,
                    headersOf(
                        HttpHeaders.ContentType,
                        ContentType.Application.Xml.withCharset(Charsets.UTF_8).toString()
                    )
                )
            } else {
                respondError(HttpStatusCode.BadRequest)
            }
        }

        var called = false
        dav.proppatch(
            setProperties = mapOf(Pair(QName("sample", "setThis"), "Some Value")),
            removeProperties = listOf(QName("sample", "removeThis"))
        ) { _, hrefRelation ->
            called = true
            assertEquals(Response.HrefRelation.SELF, hrefRelation)
        }
        assertTrue(called)
    }

    test("testProppatch_createProppatchXml") {
        val xml = DavResource.createProppatchXml(
            setProperties = mapOf(Pair(QName("sample", "setThis"), "Some Value")),
            removeProperties = listOf(QName("sample", "removeThis"))
        )
        assertEquals(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                    "<d:propertyupdate xmlns:d=\"DAV:\">" +
                    "<d:set><d:prop><n1:setThis xmlns:n1=\"sample\">Some Value</n1:setThis></d:prop></d:set>" +
                    "<d:remove><d:prop><n1:removeThis xmlns:n1=\"sample\"/></d:prop></d:remove>" +
                    "</d:propertyupdate>", xml
        )
    }

    test("testPut") {
        val url = sampleUrl
        val dav = DavResource(httpClient, url)

        /* POSITIVE TEST CASES */

        // no preconditions, 201 Created
        httpClient.changeMockHandler { request ->
            if (request.url == sampleUrl) {
                respond(
                    "",
                    HttpStatusCode.Created,
                    headersOf(HttpHeaders.ETag, "W/\"Weak PUT ETag\"")
                )
            } else {
                respondError(HttpStatusCode.BadRequest)
            }
        }

        var called = false
        dav.put(sampleText, ContentType.Text.Plain) { response ->
            called = true
            val eTag = GetETag.fromResponse(response)!!
            assertEquals("Weak PUT ETag", eTag.eTag)
            assertTrue(eTag.weak)
            assertEquals(response.request.url, dav.location)
        }
        assertTrue(called)

        var rq = httpClient.lastMockRequest
        assertEquals(HttpMethod.Put, rq.method)
        assertEquals(url.encodedPath, rq.url.encodedPath)
        assertNull(rq.headers["If-Match"])
        assertNull(rq.headers["If-None-Match"])

        // precondition: If-None-Match, 301 Moved Permanently + 204 No Content, no ETag in response
        httpClient.changeMockHandler { request ->
            if (request.url == sampleUrl) {
                respond(
                    "",
                    HttpStatusCode.MovedPermanently,
                    headersOf(HttpHeaders.Location, "/target")
                )
            } else if (request.url == sampleUrl.resolve("/target")) {
                respond(
                    "",
                    HttpStatusCode.NoContent
                )
            } else {
                respondError(HttpStatusCode.BadRequest)
            }
        }
        called = false
        dav.put(sampleText, ContentType.Text.Plain, ifNoneMatch = true) { response ->
            called = true
            assertEquals(url.resolve("/target"), response.request.url)
            val eTag = GetETag.fromResponse(response)
            eTag?.eTag.shouldBeNull()
            assertNull(eTag?.weak)
        }
        assertTrue(called)

        httpClient.lastMockRequest
        rq = httpClient.lastMockRequest
        assertEquals(HttpMethod.Put, rq.method)
        assertEquals("*", rq.headers["If-None-Match"])

        // precondition: If-Match, 412 Precondition Failed
        httpClient.changeMockHandler { request ->
            if (request.url == sampleUrl.resolve("/target")) {
                respondError(HttpStatusCode.PreconditionFailed)
            } else {
                respondError(HttpStatusCode.BadRequest)
            }
        }
        called = false
        try {
            dav.put(sampleText, ContentType.Text.Plain, "ExistingETag") {
                called = true
            }
            fail("Expected PreconditionFailedException")
        } catch (_: PreconditionFailedException) {
        }
        assertFalse(called)
        rq = httpClient.lastMockRequest
        assertEquals("\"ExistingETag\"", rq.headers["If-Match"])
        assertNull(rq.headers["If-None-Match"])
    }

    test("testSearch") {
        val url = sampleUrl
        val dav = DavResource(httpClient, url)

        httpClient.changeMockHandler { request ->
            if (request.url == sampleUrl) {
                respond(
                    "<multistatus xmlns='DAV:'>" +
                            "  <response>" +
                            "    <href>/dav</href>" +
                            "    <propstat>" +
                            "      <prop>" +
                            "        <displayname>Found something</displayname>" +
                            "      </prop>" +
                            "    </propstat>" +
                            "  </response>" +
                            "</multistatus>",
                    HttpStatusCode.MultiStatus,
                    headersOf(
                        HttpHeaders.ContentType,
                        ContentType.Application.Xml.withCharset(Charsets.UTF_8).toString()
                    )
                )
            } else {
                respondError(HttpStatusCode.BadRequest)
            }
        }

        var called = false
        dav.search("<TEST/>") { response, hrefRelation ->
            assertEquals(Response.HrefRelation.SELF, hrefRelation)
            assertEquals("Found something", response[DisplayName::class]?.displayName)
            called = true
        }
        assertTrue(called)

        val rq = httpClient.lastMockRequest
        assertEquals(DavResource.Search, rq.method)
        assertEquals(url.encodedPath, rq.url.encodedPath)
        assertEquals(
            "<TEST/>",
            io.ktor.utils.io.core.String(
                (rq.body as OutgoingContent.ByteArrayContent).bytes(),
                charset = Charsets.UTF_8
            )
        )
    }

    /** test helpers **/

    test("testAssertMultiStatus_NoBody_NoXML") {
        val ex = shouldThrow<DavException> {
            val dav = DavResource(httpClient, Url("https://from.com"))
            val response = httpClient.createResponse(buildRequest {
                url(dav.location)
            }, HttpStatusCode.MultiStatus)
            dav.assertMultiStatus(
                response
            )
        }
        ex.message.shouldBe("Got 207 Multi-Status without content!")
    }

    test("testAssertMultiStatus_NoBody_XML") {
        val ex = shouldThrow<DavException> {
            val dav = DavResource(httpClient, Url("https://from.com"))
            val response = httpClient.createResponse(buildRequest {
                url(dav.location)
            }, HttpStatusCode.MultiStatus, headersOf(HttpHeaders.ContentType, ContentType.Text.Xml.toString()))
            dav.assertMultiStatus(
                response
            )
        }
        ex.message.shouldBe("Got 207 Multi-Status without content!")
    }

    test("testAssertMultiStatus_NonXML_ButContentIsXML") {
        val dav = DavResource(httpClient, Url("https://from.com"))
        val response = httpClient.createResponse(
            buildRequest {
                url(dav.location)
            },
            HttpStatusCode.MultiStatus,
            headersOf(HttpHeaders.ContentType, ContentType.Application.OctetStream.toString()),
            "<?xml version=\"1.0\"><test/>"
        )
        dav.assertMultiStatus(
            response
        )
    }

    test("testAssertMultiStatus_NonXML_ReallyNotXML") {
        val ex = shouldThrow<DavException> {
            val dav = DavResource(httpClient, Url("https://from.com"))
            val response = httpClient.createResponse(
                buildRequest {
                    url(dav.location)
                },
                HttpStatusCode.MultiStatus,
                headersOf(HttpHeaders.ContentType, ContentType.Text.Plain.toString()),
                "Some error occurred"
            )
            dav.assertMultiStatus(
                response
            )
        }
        ex.message.shouldBe("Received non-XML 207 Multi-Status")
    }

    test("testAssertMultiStatus_Not207") {
        val ex = shouldThrow<DavException> {
            val dav = DavResource(httpClient, Url("https://from.com"))
            val response = httpClient.createResponse(
                buildRequest {
                    url(dav.location)
                },
                HttpStatusCode.Forbidden,
                headersOf(HttpHeaders.ContentType, ContentType.Application.Xml.toString()),
                ""
            )
            dav.assertMultiStatus(
                response
            )
        }
        ex.message.shouldStartWith("Expected 207 Multi-Status, got ")
    }

    test("testAssertMultiStatus_Ok_ApplicationXml") {
        val dav = DavResource(httpClient, Url("https://from.com"))
        val response = httpClient.createResponse(
            buildRequest {
                url(dav.location)
            },
            HttpStatusCode.MultiStatus,
            headersOf(HttpHeaders.ContentType, ContentType.Application.Xml.toString()),
            "<multistaus />"
        )
        dav.assertMultiStatus(
            response
        )
    }

    test("testAssertMultiStatus_Ok_TextXml") {
        val dav = DavResource(httpClient, Url("https://from.com"))
        val response = httpClient.createResponse(
            buildRequest {
                url(dav.location)
            },
            HttpStatusCode.MultiStatus,
            headersOf(HttpHeaders.ContentType, ContentType.Text.Xml.toString()),
            "<multistaus />"
        )
        dav.assertMultiStatus(
            response
        )
    }

})
