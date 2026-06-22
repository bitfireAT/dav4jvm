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
import at.bitfire.dav4jvm.ktor.exception.HttpException
import at.bitfire.dav4jvm.property.webdav.GetETag
import at.bitfire.dav4jvm.property.webdav.SyncToken
import at.bitfire.dav4jvm.property.webdav.WebDAV
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BasicAuthCredentials
import io.ktor.client.plugins.auth.providers.basic
import io.ktor.client.statement.request
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.http.withCharset
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DavCollectionTest {

    private val sampleText = "SAMPLE RESPONSE"
    private val sampleUrl = Url("http://127.0.0.1/dav/")

    private fun minimalMultiStatus() = MockEngine { _ ->
        respond(
            "<?xml version=\"1.0\" encoding=\"utf-8\"?><multistatus xmlns=\"DAV:\"/>",
            HttpStatusCode.MultiStatus,
            headersOf(HttpHeaders.ContentType, ContentType.Text.Xml.withCharset(Charsets.UTF_8).toString())
        )
    }

    private fun davCollection(engine: MockEngine) = DavCollection(HttpClient(engine), sampleUrl)

    private suspend fun requestBody(engine: MockEngine) =
        engine.requestHistory.last().body.toByteArray().toString(Charsets.UTF_8)


    @Test
    fun `reportChanges initial sync parses all members`() = runTest {
        val mockEngine = MockEngine { _ ->
            respond(
                content =
                    "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
                            "   <D:multistatus xmlns:D=\"DAV:\">\n" +
                            "     <D:response>\n" +
                            "       <D:href\n" +
                            "   >${sampleUrl}test.doc</D:href>\n" +
                            "       <D:propstat>\n" +
                            "         <D:prop>\n" +
                            "           <D:getetag>\"00001-abcd1\"</D:getetag>\n" +
                            "           <R:bigbox xmlns:R=\"urn:ns.example.com:boxschema\">\n" +
                            "             <R:BoxType>Box type A</R:BoxType>\n" +
                            "           </R:bigbox>\n" +
                            "         </D:prop>\n" +
                            "         <D:status>HTTP/1.1 200 OK</D:status>\n" +
                            "       </D:propstat>\n" +
                            "     </D:response>\n" +
                            "     <D:response>\n" +
                            "       <D:href\n" +
                            "   >${sampleUrl}vcard.vcf</D:href>\n" +
                            "       <D:propstat>\n" +
                            "         <D:prop>\n" +
                            "           <D:getetag>\"00002-abcd1\"</D:getetag>\n" +
                            "         </D:prop>\n" +
                            "         <D:status>HTTP/1.1 200 OK</D:status>\n" +
                            "       </D:propstat>\n" +
                            "       <D:propstat>\n" +
                            "         <D:prop>\n" +
                            "           <R:bigbox xmlns:R=\"urn:ns.example.com:boxschema\"/>\n" +
                            "         </D:prop>\n" +
                            "         <D:status>HTTP/1.1 404 Not Found</D:status>\n" +
                            "       </D:propstat>\n" +
                            "     </D:response>\n" +
                            "     <D:response>\n" +
                            "       <D:href\n" +
                            "   >${sampleUrl}calendar.ics</D:href>\n" +
                            "       <D:propstat>\n" +
                            "         <D:prop>\n" +
                            "           <D:getetag>\"00003-abcd1\"</D:getetag>\n" +
                            "         </D:prop>\n" +
                            "         <D:status>HTTP/1.1 200 OK</D:status>\n" +
                            "       </D:propstat>\n" +
                            "       <D:propstat>\n" +
                            "         <D:prop>\n" +
                            "           <R:bigbox xmlns:R=\"urn:ns.example.com:boxschema\"/>\n" +
                            "         </D:prop>\n" +
                            "         <D:status>HTTP/1.1 404 Not Found</D:status>\n" +
                            "       </D:propstat>\n" +
                            "     </D:response>\n" +
                            "     <D:sync-token>http://example.com/ns/sync/1234</D:sync-token>\n" +
                            "   </D:multistatus>",
                status = HttpStatusCode.MultiStatus,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Xml.withCharset(Charsets.UTF_8).toString())
            )
        }
        val collection = davCollection(mockEngine)
        var nrCalled = 0
        val result = collection.reportChanges(null, false, null, WebDAV.GetETag) { response, relation ->
            when (response.href) {
                sampleUrl.resolve("/dav/test.doc") -> {
                    assertTrue(response.isSuccess())
                    assertEquals(Response.HrefRelation.MEMBER, relation)
                    val eTag = response[GetETag::class.java]
                    assertEquals("00001-abcd1", eTag!!.eTag)
                    assertFalse(eTag.weak)
                    nrCalled++
                }

                sampleUrl.resolve("/dav/vcard.vcf") -> {
                    assertTrue(response.isSuccess())
                    assertEquals(Response.HrefRelation.MEMBER, relation)
                    val eTag = response[GetETag::class.java]
                    assertEquals("00002-abcd1", eTag!!.eTag)
                    assertFalse(eTag.weak)
                    nrCalled++
                }

                sampleUrl.resolve("/dav/calendar.ics") -> {
                    assertTrue(response.isSuccess())
                    assertEquals(Response.HrefRelation.MEMBER, relation)
                    val eTag = response[GetETag::class.java]
                    assertEquals("00003-abcd1", eTag!!.eTag)
                    assertFalse(eTag.weak)
                    nrCalled++
                }
            }
        }
        assertEquals(3, nrCalled)
        assertEquals("http://example.com/ns/sync/1234", result.filterIsInstance<SyncToken>().first().token)
    }

    @Test
    fun `reportChanges truncated parses error and sync token`() = runTest {
        val mockEngine = MockEngine { _ ->
            respond(
                content = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
                        "   <D:multistatus xmlns:D=\"DAV:\">\n" +
                        "     <D:response>\n" +
                        "       <D:href>${sampleUrl}test.doc</D:href>\n" +
                        "       <D:propstat>\n" +
                        "         <D:prop>\n" +
                        "           <D:getetag>\"00001-abcd1\"</D:getetag>\n" +
                        "         </D:prop>\n" +
                        "         <D:status>HTTP/1.1 200 OK</D:status>\n" +
                        "       </D:propstat>\n" +
                        "     </D:response>\n" +
                        "     <D:response>\n" +
                        "       <D:href>${sampleUrl}vcard.vcf</D:href>\n" +
                        "       <D:propstat>\n" +
                        "         <D:prop>\n" +
                        "           <D:getetag>\"00002-abcd1\"</D:getetag>\n" +
                        "         </D:prop>\n" +
                        "         <D:status>HTTP/1.1 200 OK</D:status>\n" +
                        "       </D:propstat>\n" +
                        "     </D:response>\n" +
                        "     <D:response>\n" +
                        "       <D:href>${sampleUrl}removed.txt</D:href>\n" +
                        "       <D:status>HTTP/1.1 404 Not Found</D:status>\n" +
                        "     </D:response>" +
                        "     <D:response>\n" +
                        "       <D:href>${sampleUrl}</D:href>\n" +
                        "       <D:status>HTTP/1.1 507 Insufficient Storage</D:status>\n" +
                        "       <D:error><D:number-of-matches-within-limits/></D:error>\n" +
                        "     </D:response>" +
                        "     <D:sync-token>http://example.com/ns/sync/1233</D:sync-token>\n" +
                        "   </D:multistatus>",
                status = HttpStatusCode.MultiStatus,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Xml.withCharset(Charsets.UTF_8).toString())
            )
        }
        val collection = davCollection(mockEngine)
        var nrCalled = 0
        val result = collection.reportChanges(null, false, null, WebDAV.GetETag) { response, relation ->
            when (response.href) {
                sampleUrl.resolve("/dav/test.doc") -> {
                    assertTrue(response.isSuccess())
                    assertEquals(Response.HrefRelation.MEMBER, relation)
                    val eTag = response[GetETag::class.java]
                    assertEquals("00001-abcd1", eTag?.eTag)
                    assertTrue(eTag?.weak == false)
                    nrCalled++
                }

                sampleUrl.resolve("/dav/vcard.vcf") -> {
                    assertTrue(response.isSuccess())
                    assertEquals(Response.HrefRelation.MEMBER, relation)
                    val eTag = response[GetETag::class.java]
                    assertEquals("00002-abcd1", eTag?.eTag)
                    assertTrue(eTag?.weak == false)
                    nrCalled++
                }

                sampleUrl.resolve("/dav/removed.txt") -> {
                    assertFalse(response.isSuccess())
                    assertEquals(404, response.status?.value)
                    assertEquals(Response.HrefRelation.MEMBER, relation)
                    nrCalled++
                }

                sampleUrl.resolve("/dav/") -> {
                    assertFalse(response.isSuccess())
                    assertEquals(507, response.status?.value)
                    assertEquals(Response.HrefRelation.SELF, relation)
                    nrCalled++
                }
            }
        }
        assertEquals("http://example.com/ns/sync/1233", result.filterIsInstance<SyncToken>().first().token)
        assertEquals(4, nrCalled)
    }

    @Test
    fun `reportChanges 507 Insufficient Storage throws HttpException`() = runTest {
        val mockEngine = MockEngine { _ ->
            respond(
                content = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
                        "   <D:error xmlns:D=\"DAV:\">\n" +
                        "     <D:number-of-matches-within-limits/>\n" +
                        "   </D:error>",
                status = HttpStatusCode.InsufficientStorage,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Xml.withCharset(Charsets.UTF_8).toString())
            )
        }
        val collection = davCollection(mockEngine)
        try {
            collection.reportChanges("http://example.com/ns/sync/1232", false, 100, WebDAV.GetETag) { _, _ -> }
            fail("Expected HttpException")
        } catch (e: HttpException) {
            assertEquals(HttpStatusCode.InsufficientStorage.value, e.statusCode)
            assertTrue(e.errors.any { it.name == Property.Name(WebDAV.NS_WEBDAV, "number-of-matches-within-limits") })
            assertEquals(1, e.errors.size)
        }
    }

    @Test
    fun `reportChanges sends REPORT with Depth 0`() = runTest {
        val engine = minimalMultiStatus()
        davCollection(engine).reportChanges(null, false, null, WebDAV.GetETag) { _, _ -> }
        with(engine.requestHistory.last()) {
            assertEquals(HttpMethod.parse("REPORT"), method)
            assertEquals("0", headers[HttpHeaders.Depth])
        }
    }

    @Test
    fun `reportChanges null sync token sends empty sync-token element`() = runTest {
        val engine = minimalMultiStatus()
        davCollection(engine).reportChanges(null, false, null, WebDAV.GetETag) { _, _ -> }
        val body = requestBody(engine)
        assertTrue(body.contains("<sync-token />"))
        assertTrue(body.contains("<sync-level>1</sync-level>"))
        assertFalse(body.contains("<limit>"))
        assertTrue(body.contains("<getetag />"))
    }

    @Test
    fun `reportChanges non-null sync token included in body`() = runTest {
        val engine = minimalMultiStatus()
        davCollection(engine).reportChanges("http://example.com/ns/sync/42", false, null, WebDAV.GetETag) { _, _ -> }
        assertTrue(requestBody(engine).contains("<sync-token>http://example.com/ns/sync/42</sync-token>"))
    }

    @Test
    fun `reportChanges infiniteDepth sends sync-level infinite`() = runTest {
        val engine = minimalMultiStatus()
        davCollection(engine).reportChanges(null, true, null, WebDAV.GetETag) { _, _ -> }
        assertTrue(requestBody(engine).contains("<sync-level>infinite</sync-level>"))
    }

    @Test
    fun `reportChanges with limit includes nresults element`() = runTest {
        val engine = minimalMultiStatus()
        davCollection(engine).reportChanges(null, false, 50, WebDAV.GetETag) { _, _ -> }
        assertTrue(requestBody(engine).contains("<nresults>50</nresults>"))
    }

    @Test
    fun `post 201 Created`() = runTest {
        val mockEngine = MockEngine { _ ->
            respond(
                content = sampleText,
                status = HttpStatusCode.Created,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Plain.toString())
            )
        }
        val dav = davCollection(mockEngine)
        var called = false
        dav.post(TextContent(sampleText, ContentType.Text.Plain)) { response ->
            assertEquals(HttpMethod.Post, response.request.method)
            assertEquals(ContentType.Text.Plain, response.request.content.contentType)
            assertEquals(HttpStatusCode.Created, response.status)
            assertEquals(response.request.url, dav.location)
            called = true
        }
        assertTrue(called)
    }

    @Test
    fun `post streaming body resent after 401`() = runTest {
        var requestCount = 0
        val mockEngine = MockEngine { request ->
            requestCount++
            assertEquals(sampleText, request.body.toByteArray().toString(Charsets.UTF_8))
            if (requestCount == 1)
                respond(
                    content = "Send Auth",
                    status = HttpStatusCode.Unauthorized,
                    headers = headersOf(HttpHeaders.WWWAuthenticate, "Basic realm=\"test\"")
                )
            else
                respond(
                    content = sampleText,
                    status = HttpStatusCode.Created,
                    headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Plain.toString())
                )
        }
        val httpClient = HttpClient(mockEngine) {
            install(Auth) {
                basic {
                    credentials { BasicAuthCredentials("test", "test") }
                }
            }
        }
        val dav = DavCollection(httpClient, sampleUrl)
        var called = false
        var channelsCreated = 0
        val streamingBody = object : OutgoingContent.ReadChannelContent() {
            override fun readFrom(): ByteReadChannel {
                channelsCreated++
                return ByteReadChannel(sampleText)
            }
            override val contentType = ContentType.Text.Plain
        }
        dav.post(streamingBody) { response ->
            assertEquals(HttpMethod.Post, response.request.method)
            assertEquals(ContentType.Text.Plain, response.request.content.contentType)
            assertEquals(HttpStatusCode.Created, response.status)
            assertEquals(response.request.url, dav.location)
            called = true
        }
        assertTrue(called)
        assertEquals(2, channelsCreated)
        assertEquals(2, requestCount)
    }

}
