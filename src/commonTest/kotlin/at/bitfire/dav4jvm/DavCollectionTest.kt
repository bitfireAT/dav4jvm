/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm

import at.bitfire.dav4jvm.exception.HttpException
import at.bitfire.dav4jvm.property.GetETag
import at.bitfire.dav4jvm.property.SyncToken
import io.kotest.core.spec.style.FunSpec
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.ktor.utils.io.charsets.*
import nl.adaptivity.xmlutil.QName
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

object DavCollectionTest : FunSpec({

    val sampleText = "SAMPLE RESPONSE"
    val sampleUrl = Url("http://mock-server.com/dav/")

    fun clientWithMocKEngine(handler: MockRequestHandler) = HttpClient(MockEngine) {
        followRedirects = false
        engine {
            addHandler(handler)
        }
    }

    fun Url.resolve(path: String) = URLBuilder(this).apply {
        takeFrom(path)
    }.build()


    /**
     * Test sample response for an initial sync-collection report from RFC 6578 3.8.
     */
    test("testInitialSyncCollectionReport") {
        val url = sampleUrl
        val httpClient = clientWithMocKEngine { request ->
            if (request.url == sampleUrl) {
                respond(
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
                    HttpStatusCode.MultiStatus,
                    headersOf(HttpHeaders.ContentType, ContentType.Text.Xml.withCharset(Charsets.UTF_8).toString())
                )
            } else {
                respondError(HttpStatusCode.BadRequest)
            }
        }
        val collection = DavCollection(httpClient, url)

        var nrCalled = 0
        val result = collection.reportChanges(null, false, null, GetETag.NAME) { response, relation ->
            when (response.href) {
                url.resolve("/dav/test.doc") -> {
                    assertTrue(response.isSuccess())
                    assertEquals(Response.HrefRelation.MEMBER, relation)
                    val eTag = response[GetETag::class]
                    assertEquals("00001-abcd1", eTag!!.eTag)
                    assertFalse(eTag.weak)
                    nrCalled++
                }

                url.resolve("/dav/vcard.vcf") -> {
                    assertTrue(response.isSuccess())
                    assertEquals(Response.HrefRelation.MEMBER, relation)
                    val eTag = response[GetETag::class]
                    assertEquals("00002-abcd1", eTag!!.eTag)
                    assertFalse(eTag.weak)
                    nrCalled++
                }

                url.resolve("/dav/calendar.ics") -> {
                    assertTrue(response.isSuccess())
                    assertEquals(Response.HrefRelation.MEMBER, relation)
                    val eTag = response[GetETag::class]
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
    test("testInitialSyncCollectionReportWithTruncation") {
        val url = sampleUrl
        val httpClient = clientWithMocKEngine { request ->
            if (request.url == sampleUrl) {
                respond(
                    "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
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
                    HttpStatusCode.MultiStatus,
                    headersOf(HttpHeaders.ContentType, ContentType.Text.Xml.withCharset(Charsets.UTF_8).toString())
                )
            } else {
                respondError(HttpStatusCode.BadRequest)
            }
        }

        val collection = DavCollection(httpClient, url)

        var nrCalled = 0
        val result = collection.reportChanges(null, false, null, GetETag.NAME) { response, relation ->
            when (response.href) {
                url.resolve("/dav/test.doc") -> {
                    assertTrue(response.isSuccess())
                    assertEquals(Response.HrefRelation.MEMBER, relation)
                    val eTag = response[GetETag::class]
                    assertEquals("00001-abcd1", eTag?.eTag)
                    assertTrue(eTag?.weak == false)
                    nrCalled++
                }

                url.resolve("/dav/vcard.vcf") -> {
                    assertTrue(response.isSuccess())
                    assertEquals(Response.HrefRelation.MEMBER, relation)
                    val eTag = response[GetETag::class]
                    assertEquals("00002-abcd1", eTag?.eTag)
                    assertTrue(eTag?.weak == false)
                    nrCalled++
                }

                url.resolve("/dav/removed.txt") -> {
                    assertFalse(response.isSuccess())
                    assertEquals(HttpStatusCode.NotFound, response.status?.status)
                    assertEquals(Response.HrefRelation.MEMBER, relation)
                    nrCalled++
                }

                url.resolve("/dav/") -> {
                    assertFalse(response.isSuccess())
                    assertEquals(HttpStatusCode.InsufficientStorage, response.status?.status)
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
    test("testSyncCollectionReportWithUnsupportedLimit") {
        val url = sampleUrl

        val httpClient = clientWithMocKEngine { request ->
            if (request.url == sampleUrl) {
                respond(
                    "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
                            "   <D:error xmlns:D=\"DAV:\">\n" +
                            "     <D:number-of-matches-within-limits/>\n" +
                            "   </D:error>",
                    HttpStatusCode.InsufficientStorage,
                    headersOf(HttpHeaders.ContentType, ContentType.Text.Xml.withCharset(Charsets.UTF_8).toString())
                )
            } else {
                respondError(HttpStatusCode.BadRequest)
            }
        }

        val collection = DavCollection(httpClient, url)

        try {
            collection.reportChanges("http://example.com/ns/sync/1232", false, 100, GetETag.NAME) { _, _ -> }
            fail("Expected HttpException")
        } catch (e: HttpException) {
            assertEquals(507, e.code)
            assertTrue(e.errors.any { it.name == QName(XmlUtils.NS_WEBDAV, "number-of-matches-within-limits") })
            assertEquals(1, e.errors.size)
        }
    }

    test("testPost") {
        val url = sampleUrl
        val httpClient = clientWithMocKEngine { request ->
            if (request.url == sampleUrl) {
                respond("", HttpStatusCode.Created)
            } else {
                respondError(HttpStatusCode.BadRequest)
            }
        }
        val dav = DavCollection(httpClient, url)

        var called = false
        dav.post(sampleText, ContentType.Text.Plain) { response ->
            val request = (httpClient.engine as MockEngine).requestHistory.first()
            assertEquals(HttpMethod.Post, request.method)
            assertEquals(request.url, dav.location)
            called = true
        }
        assertTrue(called)
    }

})