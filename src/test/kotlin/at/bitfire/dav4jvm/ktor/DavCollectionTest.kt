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
import io.ktor.client.statement.request
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.headersOf
import io.ktor.http.takeFrom
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


    /**
     * Test sample response for an initial sync-collection report from RFC 6578 3.8.
     */
    @Test
    fun testInitialSyncCollectionReport() = runTest {
        val mockEngine = MockEngine { request ->
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
        val httpClient = HttpClient(mockEngine)

        val url = sampleUrl
        val collection = DavCollection(httpClient, url)

        var nrCalled = 0
        val result = collection.reportChanges(null, false, null, WebDAV.GetETag) { response, relation ->
            when (response.href) {
                URLBuilder(url).takeFrom("/dav/test.doc").build() -> {
                    assertTrue(response.isSuccess())
                    assertEquals(Response.HrefRelation.MEMBER, relation)
                    val eTag = response[GetETag::class.java]
                    assertEquals("00001-abcd1", eTag!!.eTag)
                    assertFalse(eTag.weak)
                    nrCalled++
                }

                URLBuilder(url).takeFrom("/dav/vcard.vcf").build() -> {
                    assertTrue(response.isSuccess())
                    assertEquals(Response.HrefRelation.MEMBER, relation)
                    val eTag = response[GetETag::class.java]
                    assertEquals("00002-abcd1", eTag!!.eTag)
                    assertFalse(eTag.weak)
                    nrCalled++
                }

                URLBuilder(url).takeFrom("/dav/calendar.ics").build() -> {
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

    /**
     * Test sample response for an initial sync-collection report with truncation from RFC 6578 3.10.
     */
    @Test
    fun testInitialSyncCollectionReportWithTruncation() = runTest {
        val mockEngine = MockEngine { request ->
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
                status = HttpStatusCode.MultiStatus,  // 207
                headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Xml.withCharset(Charsets.UTF_8).toString())
            )
        }
        val httpClient = HttpClient(mockEngine)
        val collection = DavCollection(httpClient, sampleUrl)

        var nrCalled = 0

        val result = collection.reportChanges(null, false, null, WebDAV.GetETag) { response, relation ->
            when (response.href) {
                URLBuilder(sampleUrl).takeFrom("/dav/test.doc").build() -> {
                    assertTrue(response.isSuccess())
                    assertEquals(Response.HrefRelation.MEMBER, relation)
                    val eTag = response[GetETag::class.java]
                    assertEquals("00001-abcd1", eTag?.eTag)
                    assertTrue(eTag?.weak == false)
                    nrCalled++
                }

                URLBuilder(sampleUrl).takeFrom("/dav/vcard.vcf").build() -> {
                    assertTrue(response.isSuccess())
                    assertEquals(Response.HrefRelation.MEMBER, relation)
                    val eTag = response[GetETag::class.java]
                    assertEquals("00002-abcd1", eTag?.eTag)
                    assertTrue(eTag?.weak == false)
                    nrCalled++
                }

                URLBuilder(sampleUrl).takeFrom("/dav/removed.txt").build() -> {
                    assertFalse(response.isSuccess())
                    assertEquals(404, response.status?.value)
                    assertEquals(Response.HrefRelation.MEMBER, relation)
                    nrCalled++
                }

                URLBuilder(sampleUrl).takeFrom("/dav/").build() -> {
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

    /**
     * Test sample response for a sync-collection report with unsupported limit from RFC 6578 3.12.
     */
    @Test
    fun testSyncCollectionReportWithUnsupportedLimit() = runTest {
        val mockEngine = MockEngine { request ->
            respond(
                content = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
                        "   <D:error xmlns:D=\"DAV:\">\n" +
                        "     <D:number-of-matches-within-limits/>\n" +
                        "   </D:error>",
                status = HttpStatusCode.InsufficientStorage,  // 507   @Ricki, does 507 really make sense here?
                headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Xml.withCharset(Charsets.UTF_8).toString())
            )
        }
        val httpClient = HttpClient(mockEngine)
        val collection = DavCollection(httpClient, sampleUrl)

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
    fun testPost() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = sampleText,
                status = HttpStatusCode.Created,  // 201 Created
                headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Plain.toString())
            )
        }
        val httpClient = HttpClient(mockEngine)
        val dav = DavCollection(httpClient, sampleUrl)

        var called = false
        dav.post({ ByteReadChannel(sampleText) }, ContentType.Text.Plain) { response ->
            assertEquals(HttpMethod.Post, response.request.method)
            assertEquals(HttpStatusCode.Created, response.status)
            assertEquals(response.request.url, dav.location)
            called = true
        }
        assertTrue(called)
    }

}