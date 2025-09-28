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
import at.bitfire.dav4jvm.ktor.exception.PreconditionFailedException
import at.bitfire.dav4jvm.property.webdav.DisplayName
import at.bitfire.dav4jvm.property.webdav.GetETag
import at.bitfire.dav4jvm.property.webdav.ResourceType
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.engine.mock.respondOk
import io.ktor.client.engine.mock.respondRedirect
import io.ktor.client.request.get
import io.ktor.client.request.prepareRequest
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
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DavResourceTest {

    private val sampleText = "SAMPLE RESPONSE"
    val sampleUrl = Url("https://127.0.0.1/dav/")
    val sampleDestination = URLBuilder(sampleUrl).takeFrom("test").build()


    @Test
    fun `Copy POSITIVE no preconditions, 201 Created, resulted in the creation of a new resource`() {
        val mockEngine = MockEngine { request ->
            respond(
                content = sampleText,
                status = HttpStatusCode.Created,  // 201 Created
                headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Plain.toString())
            )
        }
        val httpClient = HttpClient(mockEngine)
        var called = false

        runBlocking {
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
    }

    @Test
    fun `Copy POSITIVE no preconditions, 204 No content, resource successfully copied to a preexisting destination resource`() {
        val mockEngine = MockEngine { request ->
            respond(
                content = sampleText,
                status = HttpStatusCode.NoContent,  // 204 No content
                headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Plain.toString())
            )
        }
        val httpClient = HttpClient(mockEngine)

        runBlocking {
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
    }

    @Test
    fun `Copy NEGATIVE 207 multi-status eg errors on some of resources affected by the COPY prevented the operation from taking place`() {
        val mockEngine = MockEngine { request ->
            respond(
                content = sampleText,
                status = HttpStatusCode.MultiStatus,  // 207 multi-status
                headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Plain.toString())
            )
        }
        val httpClient = HttpClient(mockEngine)
        var called = false

        runBlocking {
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
    }

    @Test
    fun `Delete only eTag POSITIVE TEST CASES  precondition If-Match 200 OK`() {

        val mockEngine = MockEngine { request ->
            respond(sampleText, HttpStatusCode.NoContent)  // 204 No Content
        }
        val httpClient = HttpClient(mockEngine)
        val dav = DavResource(httpClient, sampleUrl)
        var called = false

        runBlocking {
            dav.delete { called = true }
            assertTrue(called)
            val rq = mockEngine.requestHistory.last()
            assertEquals(HttpMethod.Delete, rq.method)
            assertEquals(sampleUrl.encodedPath, rq.url.encodedPath)
            assertNull(rq.headers[HttpHeaders.IfMatch])

        }
    }

    @Test
    fun `Delete eTag and schedule Tag POSITIVE TEST CASES  precondition If-Match 200 OK`() {

        val mockEngine = MockEngine { request ->
            respondOk(content = sampleText)
        }
        val httpClient = HttpClient(mockEngine)
        val dav = DavResource(httpClient, sampleUrl)
        var called = false

        runBlocking {
            dav.delete("DeleteOnlyThisETag", "DeleteOnlyThisScheduleTag") { called = true }
            assertTrue(called)

            val rq = mockEngine.requestHistory.last()
            assertEquals("\"DeleteOnlyThisETag\"", rq.headers[HttpHeaders.IfMatch])
            assertEquals("\"DeleteOnlyThisScheduleTag\"", rq.headers[HttpHeaders.IfScheduleTagMatch])
        }
    }


    @Test
    fun `Delete POSITIVE TEST CASES  precondition If-Match 302 Moved Temporarily`() {

        var numResponses = 0
        val mockEngine = MockEngine { request ->
            numResponses+=1
            when(numResponses) {
                1 -> respondRedirect("/new-location")  //307 TemporaryRedirect
                else -> respondOk()
            }
        }
        val httpClient = HttpClient(mockEngine)
        val dav = DavResource(httpClient, sampleUrl)
        var called = false

        runBlocking {
           dav.delete(null) {
               called = true
           }
           assertTrue(called)
        }
    }


    @Test
    fun `Delete NEGATIVE TEST CASES precondition If-Match 207 multi-status`() {

        val mockEngine = MockEngine { request ->
            respondError(HttpStatusCode.MultiStatus)
        }
        val httpClient = HttpClient(mockEngine)
        val dav = DavResource(httpClient, sampleUrl)
        var called = false

        runBlocking {
           try {
               dav.delete(null) { called = true }
               fail("Expected HttpException")
           } catch(_: HttpException) {
               assertFalse(called)
           }
        }
    }


    @Test
    fun `followRedirects 302 Found`() {
        var numResponses = 0
        val mockEngine = MockEngine { request ->
            numResponses+=1
            when(numResponses) {
                1 -> respond("New location!", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "https://to.com/"))
                else -> respond("", HttpStatusCode.NoContent, headersOf(HttpHeaders.Location, "https://to.com/"))
            }
        }
        val httpClient = HttpClient(mockEngine) {
            followRedirects = false
        }
        val dav = DavResource(httpClient, sampleUrl)

        runBlocking {
            dav.followRedirects {
                httpClient.get("https://from.com/")
            }.let { response ->
                assertEquals(HttpStatusCode.NoContent, response.status)
                assertEquals(Url("https://to.com/"), dav.location)
            }
        }
    }

    @Test(expected = DavException::class)
    fun `followRedirects Https To Http`() {
        val mockEngine = MockEngine { request ->
            respond("New location!", HttpStatusCode.Found, headersOf(HttpHeaders.Location, "http://to.com/"))
        }
        val httpClient = HttpClient(mockEngine) {
            followRedirects = false
        }

        val dav = DavResource(httpClient, Url("https://from.com"))
        runBlocking {
            dav.followRedirects {
                httpClient.get("https://from.com/")
            }
        }
    }

    @Test
    fun `Get POSITIVE TEST CASES 200 OK`() {
        val mockEngine = MockEngine { request ->
            respond(
                content = sampleText,
                status = HttpStatusCode.OK,     // 200 OK
                headers = HeadersBuilder().apply {
                    append(HttpHeaders.ETag, "W/\"My Weak ETag\"")
                    append(HttpHeaders.ContentType, "application/x-test-result")
                }.build()
            )
        }
        val httpClient = HttpClient(mockEngine)
        val dav = DavResource(httpClient, sampleUrl)
        var called = false

        runBlocking {
            dav.get(ContentType.Any.toString(), null) { response ->
                called = true
                runBlocking { assertEquals(sampleText, response.bodyAsText()) }

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
    }


    @Test
    fun `Get POSITIVE TEST CASES 302 Moved Temporarily + 200 OK`() {
        var numResponses = 0
        val mockEngine = MockEngine { request ->
            numResponses+=1
            when(numResponses) {
                1 -> respond("This resource was moved.", HttpStatusCode.TemporaryRedirect, headersOf(HttpHeaders.Location,"/target"))
                else -> respond(sampleText, HttpStatusCode.OK, headersOf(HttpHeaders.ETag,"\"StrongETag\""))
            }
        }
        val httpClient = HttpClient(mockEngine)
        val dav = DavResource(httpClient, sampleUrl)
        var called = false

        runBlocking {

            dav.get(ContentType.Any.toString(), null) { response ->
                called = true
                runBlocking { assertEquals(sampleText, response.bodyAsText()) }
                val eTag = GetETag(response.headers[HttpHeaders.ETag])
                assertEquals("StrongETag", eTag.eTag)
                assertFalse(eTag.weak)
            }
            assertTrue(called)

            val rq = mockEngine.requestHistory.last()
            assertEquals(HttpMethod.Get, rq.method)
            assertEquals("/target", rq.url.fullPath)
        }
    }


    @Test
    fun `Get POSITIVE TEST CASES 200 OK without ETag in response`() {
        val mockEngine = MockEngine { request ->
            respond(sampleText, HttpStatusCode.OK)
        }
        val httpClient = HttpClient(mockEngine)
        val dav = DavResource(httpClient, sampleUrl)
        var called = false

        runBlocking {
            dav.get(ContentType.Any.toString(), null) { response ->
                called = true
                assertNull(response.headers[HttpHeaders.ETag])
            }
            assertTrue(called)
        }
    }


    @Test
    fun `GetRange Ok`() {
        val mockEngine = MockEngine { request ->
            respond("",HttpStatusCode.PartialContent)     // 206
        }
        val httpClient = HttpClient(mockEngine)
        val dav = DavResource(httpClient, sampleUrl)

        var called = false
        runBlocking {
            dav.getRange(ContentType.Any.toString(), 100, 342) { response ->
                assertEquals("bytes=100-441", response.request.headers[HttpHeaders.Range])
                called = true
            }
            assertTrue(called)
        }
    }

    @Test
    fun `Post POSITIVE TEST CASES 200 OK`() {
        val mockEngine = MockEngine { request ->
            respond(
                content = sampleText,
                status = HttpStatusCode.OK,     // 200 OK
                headers = HeadersBuilder().apply {
                    append(HttpHeaders.ContentType, "application/x-test-result")
                    append(HttpHeaders.ETag, "W/\"My Weak ETag\"")
                }.build()
            )
        }
        val httpClient = HttpClient(mockEngine) { followRedirects = false }
        val dav = DavResource(httpClient, sampleUrl)

        /* POSITIVE TEST CASES */

        // 200 OK
        runBlocking {
            var called = false
            dav.post(
                body = "body",
                headers = HeadersBuilder().apply { append(HttpHeaders.ContentType, "application/x-test-result") }.build()
            ) { response ->
                called = true
                runBlocking { assertEquals(sampleText, response.bodyAsText()) }

                val eTag = GetETag.fromHttpResponse(response)
                assertEquals("My Weak ETag", eTag!!.eTag)
                assertTrue(eTag.weak)
                assertEquals(ContentType.parse("application/x-test-result"), response.contentType())
            }
            assertTrue(called)

            val rq = mockEngine.requestHistory.last()
            assertEquals(HttpMethod.Post, rq.method)
            assertEquals(sampleUrl.encodedPath, rq.url.encodedPath)
            assertEquals(ContentType.parse("application/x-test-result"), rq.body.contentType)  // TODO: Originally there was a check for the header, not the content type in the body, what is correct here?
            assertEquals("body", (rq.body as TextContent).text)

            /*
            // 302 Moved Temporarily + 200 OK
            called = false
            dav.post(
                body = "body",
                headers = HeadersBuilder().apply { append(HttpHeaders.ContentType, ContentType.parse("application/x-test-result").toString()) }.build()
            ) { response ->
                called = true
                runBlocking { assertEquals(sampleText, response.bodyAsText()) }
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
    }

    @Test
    fun `Post POSITIVE TEST CASES 302 Moved Temporarily + 200 OK`() {
        var numResponses = 0
        val mockEngine = MockEngine { request ->
            numResponses+=1
            when(numResponses) {
                1 -> respond("This resource was moved.", HttpStatusCode.TemporaryRedirect, headersOf(HttpHeaders.Location,"/target"))
                else -> respond(sampleText, HttpStatusCode.OK, headersOf(HttpHeaders.ETag,"\"StrongETag\""))
            }
        }
        val httpClient = HttpClient(mockEngine) { followRedirects = false }
        val dav = DavResource(httpClient, sampleUrl)

        runBlocking {
            var called = false
            dav.post(
                body = "body",
                headers = HeadersBuilder().apply { append(HttpHeaders.ContentType, ContentType.parse("application/x-test-result").toString()) }.build()
            ) { response ->
                called = true
                runBlocking { assertEquals(sampleText, response.bodyAsText()) }
                val eTag = GetETag(response.headers[HttpHeaders.ETag])
                assertEquals("StrongETag", eTag.eTag)
                assertFalse(eTag.weak)
            }
            assertTrue(called)

            val rq = mockEngine.requestHistory.last()
            assertEquals(HttpMethod.Post, rq.method)
            assertEquals("/target", rq.url.encodedPath)
        }
    }

    @Test
    fun `Post POSITIVE TEST CASES 200 OK without ETag in response`() {
        val mockEngine = MockEngine { request ->
            respond(sampleText, HttpStatusCode.OK)
        }
        val httpClient = HttpClient(mockEngine) { followRedirects = false }
        val dav = DavResource(httpClient, sampleUrl)

        runBlocking {
            var called = false
            dav.post(
                body = "body",
            ) { response ->
                called = true
                assertNull(response.headers[HttpHeaders.ETag])
            }
            assertTrue(called)
        }
    }

  @Test
  fun `Move POSITIVE TEST CASES no preconditions, 201 Created, new URL mapping at the destination`() {
      val mockEngine = MockEngine { request ->
          respond("",HttpStatusCode.Created)     // 201 Created
      }
      val httpClient = HttpClient(mockEngine) { followRedirects = false }
      val destination = URLBuilder(sampleUrl).takeFrom("test").build()

      runBlocking {
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
  }

    @Test
    fun `Move POSITIVE TEST CASES no preconditions, 204 No content, URL already mapped, overwrite`() {
        val mockEngine = MockEngine { request ->
            respond("",HttpStatusCode.NoContent)     // 204 No content
        }
        val httpClient = HttpClient(mockEngine) { followRedirects = false }
        val destination = URLBuilder(sampleUrl).takeFrom("test").build()

        runBlocking {
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
    }

    @Test
    fun `Move NEGATIVE TEST CASES no preconditions, 207 multi-status`() {
        val mockEngine = MockEngine { request ->
            respond("",HttpStatusCode.MultiStatus)     // 207 Multi-Status
        }
        val httpClient = HttpClient(mockEngine) { followRedirects = false }
        val destination = URLBuilder(sampleUrl).takeFrom("test").build()

        runBlocking {
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
    }

  @Test
  fun `Options with capabilities`() {
      val mockEngine = MockEngine { request ->
          respond("",HttpStatusCode.OK, HeadersBuilder().apply { append("DAV", "  1,  2 ,3,hyperactive-access")}.build())     // 200 Ok
      }
      val httpClient = HttpClient(mockEngine) { followRedirects = false }
      val dav = DavResource(httpClient, sampleUrl)

      runBlocking {
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
  }


    @Test
    fun `Options without capabilities`() {
        val mockEngine = MockEngine { request ->
            respondOk()     // 200 OK
        }
        val httpClient = HttpClient(mockEngine) { followRedirects = false }
        val dav = DavResource(httpClient, sampleUrl)

        runBlocking {
            var called = false
            dav.options { davCapabilities, _ ->
                called = true
                assertTrue(davCapabilities.isEmpty())
            }
            assertTrue(called)
        }
    }

  @Test
  fun `NEGATIVE TEST CASES Propfind And MultiStatus 500 Internal Server Error`() {
      val mockEngine = MockEngine { request ->
          respondError(HttpStatusCode.InternalServerError)     // 500
      }
      val httpClient = HttpClient(mockEngine) { followRedirects = false }
      val dav = DavResource(httpClient, sampleUrl)

      runBlocking {
          var called = false
          try {
              dav.propfind(0, ResourceType.NAME) { _, _ -> called = true }
              fail("Expected HttpException")
          } catch (_: HttpException) {
              assertFalse(called)
          }

      }
  }

    @Test
    fun `NEGATIVE TEST CASES Propfind And MultiStatus 200 OK (instead of 207 Multi-Status)`() {
        val mockEngine = MockEngine { request -> respondOk() }
        val httpClient = HttpClient(mockEngine) { followRedirects = false }
        val dav = DavResource(httpClient, sampleUrl)

        runBlocking {
            // * 200 OK (instead of 207 Multi-Status)
            var called = false
            try {
                dav.propfind(0, ResourceType.NAME) { _, _ -> called = true }
                fail("Expected DavException")
            } catch (_: DavException) {
                assertFalse(called)
            }
        }
    }

    @Test
    fun `NEGATIVE TEST CASES Propfind And MultiStatus non-XML response`() {
        val mockEngine = MockEngine { request ->
            respond("<html></html>", HttpStatusCode.MultiStatus, headersOf(HttpHeaders.ContentType, ContentType.Text.Html.toString()))   // non-XML response
        }
        val httpClient = HttpClient(mockEngine) { followRedirects = false }
        val dav = DavResource(httpClient, sampleUrl)

        runBlocking {
            // * non-XML response
            var called = false
            try {
                dav.propfind(0, ResourceType.NAME) { _, _ -> called = true }
                fail("Expected DavException")
            } catch (_: DavException) {
                assertFalse(called)
            }
        }
    }

    @Test
    fun `NEGATIVE TEST CASES Propfind And MultiStatus malformed XML response`() {
        val mockEngine = MockEngine { request ->
            respond("<malformed-xml>", HttpStatusCode.MultiStatus, headersOf(HttpHeaders.ContentType, ContentType.Application.Xml.withCharset(Charsets.UTF_8).toString()))   // * malformed XML response
        }
        val httpClient = HttpClient(mockEngine) { followRedirects = false }
        val dav = DavResource(httpClient, sampleUrl)

        runBlocking {
            // * malformed XML response
            var called = false
            try {
                dav.propfind(0, ResourceType.NAME) { _, _ -> called = true }
                fail("Expected DavException")
            } catch (_: DavException) {
                assertFalse(called)
            }
        }
    }


    @Test
    fun `NEGATIVE TEST CASES Propfind And MultiStatus response without multistatus root element`() {
        val mockEngine = MockEngine { request ->
            respond("<test></test>", HttpStatusCode.MultiStatus, headersOf(HttpHeaders.ContentType, ContentType.Application.Xml.withCharset(Charsets.UTF_8).toString()))   // * response without <multistatus> root element
        }
        val httpClient = HttpClient(mockEngine) { followRedirects = false }
        val dav = DavResource(httpClient, sampleUrl)

        runBlocking {
            // * response without <multistatus> root element
            var called = false
            try {
                dav.propfind(0, ResourceType.NAME) { _, _ -> called = true }
                fail("Expected DavException")
            } catch (_: DavException) {
                assertFalse(called)
            }
        }
    }


    @Test
    fun `NEGATIVE TEST CASES Propfind And MultiStatus multi-status response with invalid status in response`() {
        val mockEngine = MockEngine { request ->
            respond("<multistatus xmlns='DAV:'>" +
                    "  <response>" +
                    "    <href>/dav</href>" +
                    "    <status>Invalid Status Line</status>" +
                    "  </response>" +
                    "</multistatus>",
                HttpStatusCode.MultiStatus,
                headersOf(HttpHeaders.ContentType, ContentType.Application.Xml.withCharset(Charsets.UTF_8).toString())
            )   // * multi-status response with invalid <status> in <response>
        }
        val httpClient = HttpClient(mockEngine) { followRedirects = false }
        val dav = DavResource(httpClient, sampleUrl)

        runBlocking {
            // * multi-status response with invalid <status> in <response>
            var called = false
            dav.propfind(0, ResourceType.NAME) { response, relation ->
                assertEquals(Response.HrefRelation.SELF, relation)
                assertEquals(HttpStatusCode.InternalServerError, response.status)
                called = true
            }
            assertTrue(called)
        }
    }

    @Test
    fun `NEGATIVE TEST CASES Propfind And MultiStatus multi-status response with response-status element indicating failure`() {
        val mockEngine = MockEngine { request ->
            respond("<multistatus xmlns='DAV:'>" +
                    "  <response>" +
                    "    <href>/dav</href>" +
                    "    <status>HTTP/1.1 403 Forbidden</status>" +
                    "  </response>" +
                    "</multistatus>",
                HttpStatusCode.MultiStatus,
                headersOf(HttpHeaders.ContentType, ContentType.Application.Xml.withCharset(Charsets.UTF_8).toString())
            )   // * multi-status response with <response>/<status> element indicating failure
        }
        val httpClient = HttpClient(mockEngine) { followRedirects = false }
        val dav = DavResource(httpClient, sampleUrl)

        runBlocking {
            // * multi-status response with <response>/<status> element indicating failure
            var called = false
            dav.propfind(0, ResourceType.NAME) { response, relation ->
                assertEquals(Response.HrefRelation.SELF, relation)
                assertEquals(HttpStatusCode.Forbidden, response.status)
                called = true
            }
            assertTrue(called)
        }
    }

    @Test
    fun `NEGATIVE TEST CASES Propfind And MultiStatus multi-status response with invalid status in propstat`() {
        val mockEngine = MockEngine { request ->
            respond("<multistatus xmlns='DAV:'>" +
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
                headersOf(HttpHeaders.ContentType, ContentType.Application.Xml.withCharset(Charsets.UTF_8).toString())
            )   // * multi-status response with invalid <status> in <propstat>
        }
        val httpClient = HttpClient(mockEngine) { followRedirects = false }
        val dav = DavResource(httpClient, sampleUrl)

        runBlocking {
            // * multi-status response with invalid <status> in <propstat>
            var called = false
            dav.propfind(0, ResourceType.NAME) { response, relation ->
                called = true
                assertEquals(Response.HrefRelation.SELF, relation)
                assertTrue(response.properties.filterIsInstance<ResourceType>().isEmpty())
            }
            assertTrue(called)

        }
    }


    @Test
    fun `NEGATIVE TEST CASES Propfind And MultiStatus multi-status response without response elements`() {
        val mockEngine = MockEngine { request ->
            respond("<multistatus xmlns='DAV:'></multistatus>",
                HttpStatusCode.MultiStatus,
                headersOf(HttpHeaders.ContentType, ContentType.Application.Xml.withCharset(Charsets.UTF_8).toString())
            )   // multi-status response without <response> elements
        }
        val httpClient = HttpClient(mockEngine) { followRedirects = false }
        val dav = DavResource(httpClient, sampleUrl)

        runBlocking {
            // multi-status response without <response> elements
            dav.propfind(0, ResourceType.NAME) { _, _ ->
                fail("Shouldn't be called")
            }
        }
    }

    @Test
    fun `POSITIVE TEST CASES Propfind And MultiStatus multi-status response with response-status element indicating success`() {
        val mockEngine = MockEngine { request ->
            respond( "<multistatus xmlns='DAV:'>" +
                    "  <response>" +
                    "    <href>/dav</href>" +
                    "    <status>HTTP/1.1 200 OK</status>" +
                    "  </response>" +
                    "</multistatus>",
                HttpStatusCode.MultiStatus,
                headersOf(HttpHeaders.ContentType, ContentType.Application.Xml.withCharset(Charsets.UTF_8).toString())
            )   // multi-status response with <response>/<status> element indicating success
        }
        val httpClient = HttpClient(mockEngine) { followRedirects = false }
        val dav = DavResource(httpClient, sampleUrl)

        runBlocking {
            var called = false
            // multi-status response with <response>/<status> element indicating success
            dav.propfind(0, ResourceType.NAME) { response, relation ->
                called = true
                assertTrue(response.isSuccess())
                assertEquals(Response.HrefRelation.SELF, relation)
                assertEquals(0, response.properties.size)
            }
            assertTrue(called)
        }
    }


    @Test
    fun `POSITIVE TEST CASES Propfind And MultiStatus multi-status response with response-propstat element`() {
        val mockEngine = MockEngine { request ->
            respond( "<multistatus xmlns='DAV:'>" +
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
        val httpClient = HttpClient(mockEngine) { followRedirects = false }
        val dav = DavResource(httpClient, sampleUrl)

        runBlocking {
            var called = false
            dav.propfind(0, ResourceType.NAME, DisplayName.NAME) { response, relation ->
                called = true
                assertTrue(response.isSuccess())
                assertEquals(Response.HrefRelation.SELF, relation)
                assertEquals("My DAV Collection", response[DisplayName::class.java]?.displayName)
            }
            assertTrue(called)
        }
    }

    @Test
    fun `POSITIVE TEST CASES Propfind And MultiStatus SPECIAL CASES multi-status response for collection with several members, incomplete (not all resourcetypes listed)`() {
        val mockEngine = MockEngine { request ->
            respond( "<multistatus xmlns='DAV:'>" +
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
        val httpClient = HttpClient(mockEngine) { followRedirects = false }
        val dav = DavResource(httpClient, sampleUrl)

        runBlocking {
            // multi-status response for collection with several members; incomplete (not all <resourcetype>s listed)
            var nrCalled = 0
            dav.propfind(1, ResourceType.NAME, DisplayName.NAME) { response, relation ->
                when (response.href) {
                    URLBuilder(sampleUrl).takeFrom("/dav/").build() -> {
                        assertTrue(response.isSuccess())
                        assertEquals(Response.HrefRelation.SELF, relation)
                        assertTrue(response[ResourceType::class.java]!!.types.contains(ResourceType.COLLECTION))
                        assertEquals("My DAV Collection", response[DisplayName::class.java]?.displayName)
                        nrCalled++
                    }

                    URLBuilder(sampleUrl).takeFrom("/dav/subcollection/").build() -> {
                        assertTrue(response.isSuccess())
                        assertEquals(Response.HrefRelation.MEMBER, relation)
                        assertTrue(response[ResourceType::class.java]!!.types.contains(ResourceType.COLLECTION))
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
            assertEquals(4, nrCalled)
        }
    }

    @Test
    fun `POSITIVE TEST CASES Propfind And MultiStatus SPECIAL CASES same property is sent as 200 OK and 404 Not Found in same response (seen in iCloud)`() {
        val mockEngine = MockEngine { request ->
            respond( "<multistatus xmlns='DAV:'>" +
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
        val httpClient = HttpClient(mockEngine) { followRedirects = false }
        val dav = DavResource(httpClient, sampleUrl)

        runBlocking {
            var called = false
            dav.propfind(0, ResourceType.NAME, DisplayName.NAME) { response, relation ->
                called = true
                assertTrue(response.isSuccess())
                assertEquals(Response.HrefRelation.SELF, relation)
                assertEquals(URLBuilder(sampleUrl).takeFrom("/dav/").build(), response.href)
                assertTrue(response[ResourceType::class.java]!!.types.contains(ResourceType.COLLECTION))
                assertEquals("My DAV Collection", response[DisplayName::class.java]?.displayName)
            }
            assertTrue(called)
        }
    }


    @Test
    fun `POSITIVE TEST CASES Propfind And MultiStatus SPECIAL CASES multi-status response with propstat that doesn't contain status, assume 200 OK`() {
        val mockEngine = MockEngine { request ->
            respond( "<multistatus xmlns='DAV:'>" +
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
        val httpClient = HttpClient(mockEngine) { followRedirects = false }
        val dav = DavResource(httpClient, sampleUrl)

        runBlocking {
            /*** SPECIAL CASES ***/

            // multi-status response with <propstat> that doesn't contain <status> (=> assume 200 OK)
            var called = false
            dav.propfind(0, DisplayName.NAME) { response, _ ->
                called = true
                assertEquals(200, response.propstat.first().status.value)
                assertEquals("Without Status", response[DisplayName::class.java]?.displayName)
            }
            assertTrue(called)
        }
    }

    @Test
    fun `Proppatch`() {
        // multi-status response with <response>/<propstat> elements
        val mockEngine = MockEngine { request ->
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
        runBlocking {
            dav.proppatch(
                setProperties = mapOf(Pair(Property.Name("sample", "setThis"), "Some Value")),
                removeProperties = listOf(Property.Name("sample", "removeThis"))
            ) { _, hrefRelation ->
                called = true
                assertEquals(Response.HrefRelation.SELF, hrefRelation)
            }
            assertTrue(called)
        }
    }


    @Test
    fun `Proppatch createProppatchXml`() {
        val xml = DavResource.createProppatchXml(
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
    fun `Put POSITIVE TEST CASES no preconditions, 201 Created`() {
        val mockEngine = MockEngine { request ->
            respond(
                content = " ",
                status = HttpStatusCode.Created,  // 201 Created
                headers = headersOf(HttpHeaders.ETag, "W/\"Weak PUT ETag\"")
            )
        }

        val httpClient = HttpClient(mockEngine)
        val dav = DavResource(httpClient, sampleUrl)

        var called = false
        runBlocking {
        dav.put(
            body = sampleText,
            headers = HeadersBuilder().apply { append(HttpHeaders.ContentType, "text/plain") }.build()
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
    }

    @Test
    fun `Put POSITIVE TEST CASES precondition  If-None-Match, 301 Moved Permanently + 204 No Content, no ETag in response`() {
        var numberOfResponse = 0
        val mockEngine = MockEngine { request ->
            numberOfResponse+=1
            when(numberOfResponse) {
                1 -> respond(
                    content = "",
                    status = HttpStatusCode.MovedPermanently,  // 301 Moved Permanently
                    headers = headersOf(HttpHeaders.Location, "/target")
                )
                else -> respond("", HttpStatusCode.NoContent)
            }
        }

        val httpClient = HttpClient(mockEngine)
        val dav = DavResource(httpClient, sampleUrl)

        var called = false
        runBlocking {
        dav.put(sampleText, headersOf(HttpHeaders.ContentType, ContentType.Text.Plain.toString()), ifNoneMatch = true) { response ->
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
    }

    @Test
    fun `Put NEGATIVE TEST CASES precondition  If-Match, 412 Precondition Failed`() {
        val mockEngine = MockEngine { request ->
            respond("", HttpStatusCode.PreconditionFailed)
        }

        val httpClient = HttpClient(mockEngine)
        val dav = DavResource(httpClient, sampleUrl)

        var called = false
        runBlocking {
                    try {
                        dav.put(sampleText, headersOf(HttpHeaders.ContentType, ContentType.Text.Plain.toString()), "ExistingETag") {
                            called = true
                        }
                        fail("Expected PreconditionFailedException")
                    } catch(_: PreconditionFailedException) {}
                    assertFalse(called)
                    val rq = mockEngine.requestHistory.last()
                    assertEquals("\"ExistingETag\"", rq.headers[HttpHeaders.IfMatch])
                    assertNull(rq.headers[HttpHeaders.IfNoneMatch])
        }
    }

    @Test
    fun `Search`() {
        val mockEngine = MockEngine { request ->
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

        runBlocking {
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
    }


    /** test helpers **/

    @Test
    fun `AssertMultiStatus EmptyBody NoXML`() {
        val mockEngine = MockEngine { request ->
            respond(
                content = "",
                status = HttpStatusCode.MultiStatus  // 207 Multi-Status
            )
        }
        val httpClient = HttpClient(mockEngine)

        runBlocking {
            val dav = DavResource(httpClient, Url("https://from.com"))
            dav.assertMultiStatus(httpClient.prepareRequest(dav.location).execute())
        }
    }

    @Test
    fun `AssertMultiStatus EmptyBody XML`() {
        val mockEngine = MockEngine { request ->
            respond(
                content = "",
                status = HttpStatusCode.MultiStatus,  // 207 Multi-Status
                headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Xml.toString())
            )
        }
        val httpClient = HttpClient(mockEngine)

        runBlocking {
            val dav = DavResource(httpClient, Url("https://from.com"))
            dav.assertMultiStatus(httpClient.prepareRequest(dav.location).execute())
        }
    }

    @Test
    fun `AssertMultiStatus NonXML ButContentIsXML`() {
        val mockEngine = MockEngine { request ->
            respond(
                content = "<?xml version=\"1.0\"><test/>",
                status = HttpStatusCode.MultiStatus,  // 207 Multi-Status
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.OctetStream.toString())
            )
        }
        val httpClient = HttpClient(mockEngine)

        runBlocking {
            val dav = DavResource(httpClient, Url("https://from.com"))
            dav.assertMultiStatus(httpClient.prepareRequest(dav.location).execute())
        }
    }

    @Test(expected = DavException::class)
    fun `AssertMultiStatus NonXML Really Not XML`() {
        val mockEngine = MockEngine { request ->
            respond(
                content = "Some error occurred",
                status = HttpStatusCode.MultiStatus,  // 207 Multi-Status
                headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Plain.toString())
            )
        }
        val httpClient = HttpClient(mockEngine)

        runBlocking {
            val dav = DavResource(httpClient, Url("https://from.com"))
            dav.assertMultiStatus(httpClient.prepareRequest(dav.location).execute())
        }

    }

    @Test(expected = DavException::class)
    fun `AssertMultiStatus Not 207`() {

        val mockEngine = MockEngine { request ->
            respond(
                content = "",
                status = HttpStatusCode(403, "Multi-Status"),  // 207 Multi-Status
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Xml.toString())
            )
        }
        val httpClient = HttpClient(mockEngine)

        runBlocking {
            val dav = DavResource(httpClient, Url("https://from.com"))
            dav.assertMultiStatus(httpClient.prepareRequest(dav.location).execute())
        }

    }

    @Test
    fun `AssertMultiStatus Ok ApplicationXml`() {

        val mockEngine = MockEngine { request ->
            respond(
                content = "",
                status = HttpStatusCode.MultiStatus,  // 207 Multi-Status
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Xml.toString())
            )
        }
        val httpClient = HttpClient(mockEngine)

        runBlocking {
            val dav = DavResource(httpClient, Url("https://from.com"))
            dav.assertMultiStatus(httpClient.prepareRequest(dav.location).execute())
        }
    }

    @Test
    fun `AssertMultiStatus Ok TextXml`() {

        val mockEngine = MockEngine { request ->
            respond(
                content = "",
                status = HttpStatusCode.MultiStatus,  // 207 Multi-Status
                headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Xml.toString())
            )
        }
        val httpClient = HttpClient(mockEngine)
        val dav = DavResource(httpClient, Url("https://from.com"))
        runBlocking {
            dav.assertMultiStatus(httpClient.prepareRequest(dav.location).execute())
        }
    }
}
