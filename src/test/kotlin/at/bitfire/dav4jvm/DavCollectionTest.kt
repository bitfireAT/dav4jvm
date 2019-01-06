/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm

import at.bitfire.dav4jvm.exception.HttpException
import at.bitfire.dav4jvm.property.GetETag
import at.bitfire.dav4jvm.property.SyncToken
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class DavCollectionTest {

    private val httpClient = OkHttpClient.Builder()
            .followRedirects(false)
            .build()
    private val mockServer = MockWebServer()
    private fun sampleUrl() = mockServer.url("/dav/")

    @Before
    fun startServer() = mockServer.start()

    @After
    fun stopServer() = mockServer.shutdown()


    /**
     * Test sample response for an initial sync-collection report from RFC 6578 3.8.
     */
    @Test
    fun testInitialSyncCollectionReport() {
        val url = sampleUrl()
        val collection = DavCollection(httpClient, url)

        mockServer.enqueue(MockResponse()
                .setResponseCode(207)
                .setHeader("Content-Type", "text/xml; charset=\"utf-8\"")
                .setBody("<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
                        "   <D:multistatus xmlns:D=\"DAV:\">\n" +
                        "     <D:response>\n" +
                        "       <D:href\n" +
                        "   >${sampleUrl()}test.doc</D:href>\n" +
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
                        "   >${sampleUrl()}vcard.vcf</D:href>\n" +
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
                        "   >${sampleUrl()}calendar.ics</D:href>\n" +
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
                        "   </D:multistatus>")
        )
        var nrCalled = 0
        val result = collection.reportChanges(null, false, null, GetETag.NAME) { response, relation ->
            when (response.href) {
                url.resolve("/dav/test.doc") -> {
                    assertTrue(response.isSuccess())
                    assertEquals(Response.HrefRelation.MEMBER, relation)
                    assertEquals("00001-abcd1", response[GetETag::class.java]?.eTag)
                    nrCalled++
                }
                url.resolve("/dav/vcard.vcf") -> {
                    assertTrue(response.isSuccess())
                    assertEquals(Response.HrefRelation.MEMBER, relation)
                    assertEquals("00002-abcd1", response[GetETag::class.java]?.eTag)
                    nrCalled++
                }
                url.resolve("/dav/calendar.ics") -> {
                    assertTrue(response.isSuccess())
                    assertEquals(Response.HrefRelation.MEMBER, relation)
                    assertEquals("00003-abcd1", response[GetETag::class.java]?.eTag)
                    nrCalled++
                }
            }
        }
        assertEquals(3, nrCalled)
        assertEquals("http://example.com/ns/sync/1234", result.filterIsInstance(SyncToken::class.java).first().token)
    }

    /**
     * Test sample response for an initial sync-collection report with truncation from RFC 6578 3.10.
     */
    @Test
    fun testInitialSyncCollectionReportWithTruncation() {
        val url = sampleUrl()
        val collection = DavCollection(httpClient, url)

        mockServer.enqueue(MockResponse()
                .setResponseCode(207)
                .setHeader("Content-Type", "text/xml; charset=\"utf-8\"")
                .setBody("<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
                        "   <D:multistatus xmlns:D=\"DAV:\">\n" +
                        "     <D:response>\n" +
                        "       <D:href>${sampleUrl()}test.doc</D:href>\n" +
                        "       <D:propstat>\n" +
                        "         <D:prop>\n" +
                        "           <D:getetag>\"00001-abcd1\"</D:getetag>\n" +
                        "         </D:prop>\n" +
                        "         <D:status>HTTP/1.1 200 OK</D:status>\n" +
                        "       </D:propstat>\n" +
                        "     </D:response>\n" +
                        "     <D:response>\n" +
                        "       <D:href>${sampleUrl()}vcard.vcf</D:href>\n" +
                        "       <D:propstat>\n" +
                        "         <D:prop>\n" +
                        "           <D:getetag>\"00002-abcd1\"</D:getetag>\n" +
                        "         </D:prop>\n" +
                        "         <D:status>HTTP/1.1 200 OK</D:status>\n" +
                        "       </D:propstat>\n" +
                        "     </D:response>\n" +
                        "     <D:response>\n" +
                        "       <D:href>${sampleUrl()}removed.txt</D:href>\n" +
                        "       <D:status>HTTP/1.1 404 Not Found</D:status>\n" +
                        "     </D:response>" +
                        "     <D:response>\n" +
                        "       <D:href>${sampleUrl()}</D:href>\n" +
                        "       <D:status>HTTP/1.1 507 Insufficient Storage</D:status>\n" +
                        "       <D:error><D:number-of-matches-within-limits/></D:error>\n" +
                        "     </D:response>" +
                        "     <D:sync-token>http://example.com/ns/sync/1233</D:sync-token>\n" +
                        "   </D:multistatus>")
        )
        var nrCalled = 0
        val result = collection.reportChanges(null, false, null, GetETag.NAME) { response, relation ->
            when (response.href) {
                url.resolve("/dav/test.doc") -> {
                    assertTrue(response.isSuccess())
                    assertEquals(Response.HrefRelation.MEMBER, relation)
                    assertEquals("00001-abcd1", response[GetETag::class.java]?.eTag)
                    nrCalled++
                }
                url.resolve("/dav/vcard.vcf") -> {
                    assertTrue(response.isSuccess())
                    assertEquals(Response.HrefRelation.MEMBER, relation)
                    assertEquals("00002-abcd1", response[GetETag::class.java]?.eTag)
                    nrCalled++
                }
                url.resolve("/dav/removed.txt") -> {
                    assertFalse(response.isSuccess())
                    assertEquals(404, response.status?.code)
                    assertEquals(Response.HrefRelation.MEMBER, relation)
                    nrCalled++
                }
                url.resolve("/dav/") -> {
                    assertFalse(response.isSuccess())
                    assertEquals(507, response.status?.code)
                    assertEquals(Response.HrefRelation.SELF, relation)
                    nrCalled++
                }
            }
        }
        assertEquals("http://example.com/ns/sync/1233", result.filterIsInstance(SyncToken::class.java).first().token)
        assertEquals(4, nrCalled)
    }

    /**
     * Test sample response for a sync-collection report with unsupported limit from RFC 6578 3.12.
     */
    @Test
    fun testSyncCollectionReportWithUnsupportedLimit() {
        val url = sampleUrl()
        val collection = DavCollection(httpClient, url)

        mockServer.enqueue(MockResponse()
                .setResponseCode(507)
                .setHeader("Content-Type", "text/xml; charset=\"utf-8\"")
                .setBody("<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
                        "   <D:error xmlns:D=\"DAV:\">\n" +
                        "     <D:number-of-matches-within-limits/>\n" +
                        "   </D:error>")
        )

        try {
            collection.reportChanges("http://example.com/ns/sync/1232", false, 100, GetETag.NAME) { _, _ ->  }
            fail("Expected HttpException")
        } catch (e: HttpException) {
            assertEquals(507, e.code)
            assertTrue(e.errors.any { it.name == Property.Name(XmlUtils.NS_WEBDAV, "number-of-matches-within-limits") })
            assertEquals(1, e.errors.size)
        }
    }

}