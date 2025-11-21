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
import at.bitfire.dav4jvm.okhttp.exception.HttpException
import at.bitfire.dav4jvm.property.webdav.GetETag
import at.bitfire.dav4jvm.property.webdav.SyncToken
import at.bitfire.dav4jvm.property.webdav.WebDAV
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.net.HttpURLConnection

class DavCollectionTest {

    private val sampleText = "SAMPLE RESPONSE"

    private val httpClient = OkHttpClient.Builder()
            .followRedirects(false)
            .build()
    private val mockServer = MockWebServer()
    private fun sampleUrl() = mockServer.url("/dav/")

    @Before
    fun startServer() = mockServer.start()

    @After
    fun stopServer() = mockServer.close()


    /**
     * Test sample response for an initial sync-collection report from RFC 6578 3.8.
     */
    @Test
    fun testInitialSyncCollectionReport() {
        val url = sampleUrl()
        val collection = DavCollection(httpClient, url)

        mockServer.enqueue(
            MockResponse.Builder()
                .code(207)
                .setHeader("Content-Type", "text/xml; charset=\"utf-8\"")
                .body(
                    "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
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
                            "   </D:multistatus>"
                )
                .build()
        )
        var nrCalled = 0
        val result = collection.reportChanges(null, false, null, WebDAV.GetETag) { response, relation ->
            when (response.href) {
                url.resolve("/dav/test.doc") -> {
                    assertTrue(response.isSuccess())
                    assertEquals(Response.HrefRelation.MEMBER, relation)
                    val eTag = response[GetETag::class.java]
                    assertEquals("00001-abcd1", eTag!!.eTag)
                    assertFalse(eTag.weak)
                    nrCalled++
                }
                url.resolve("/dav/vcard.vcf") -> {
                    assertTrue(response.isSuccess())
                    assertEquals(Response.HrefRelation.MEMBER, relation)
                    val eTag = response[GetETag::class.java]
                    assertEquals("00002-abcd1", eTag!!.eTag)
                    assertFalse(eTag.weak)
                    nrCalled++
                }
                url.resolve("/dav/calendar.ics") -> {
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
    fun testInitialSyncCollectionReportWithTruncation() {
        val url = sampleUrl()
        val collection = DavCollection(httpClient, url)

        mockServer.enqueue(
            MockResponse.Builder()
                .code(207)
                .setHeader("Content-Type", "text/xml; charset=\"utf-8\"")
                .body(
                    "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
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
                            "   </D:multistatus>"
                )
                .build()
        )
        var nrCalled = 0
        val result = collection.reportChanges(null, false, null, WebDAV.GetETag) { response, relation ->
            when (response.href) {
                url.resolve("/dav/test.doc") -> {
                    assertTrue(response.isSuccess())
                    assertEquals(Response.HrefRelation.MEMBER, relation)
                    val eTag = response[GetETag::class.java]
                    assertEquals("00001-abcd1", eTag?.eTag)
                    assertTrue(eTag?.weak == false)
                    nrCalled++
                }
                url.resolve("/dav/vcard.vcf") -> {
                    assertTrue(response.isSuccess())
                    assertEquals(Response.HrefRelation.MEMBER, relation)
                    val eTag = response[GetETag::class.java]
                    assertEquals("00002-abcd1", eTag?.eTag)
                    assertTrue(eTag?.weak == false)
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
        assertEquals("http://example.com/ns/sync/1233", result.filterIsInstance<SyncToken>().first().token)
        assertEquals(4, nrCalled)
    }

    /**
     * Test sample response for a sync-collection report with unsupported limit from RFC 6578 3.12.
     */
    @Test
    fun testSyncCollectionReportWithUnsupportedLimit() {
        val url = sampleUrl()
        val collection = DavCollection(httpClient, url)

        mockServer.enqueue(
            MockResponse.Builder()
                .code(507)
                .setHeader("Content-Type", "text/xml; charset=\"utf-8\"")
                .body(
                    "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
                            "   <D:error xmlns:D=\"DAV:\">\n" +
                            "     <D:number-of-matches-within-limits/>\n" +
                            "   </D:error>"
                )
                .build()
        )

        try {
            collection.reportChanges("http://example.com/ns/sync/1232", false, 100, WebDAV.GetETag) { _, _ ->  }
            fail("Expected HttpException")
        } catch (e: HttpException) {
            assertEquals(507, e.statusCode)
            assertTrue(e.errors.any { it.name == Property.Name(WebDAV.NS_WEBDAV, "number-of-matches-within-limits") })
            assertEquals(1, e.errors.size)
        }
    }

    @Test
    fun testPost() {
        val url = sampleUrl()
        val dav = DavCollection(httpClient, url)

        // 201 Created
        mockServer.enqueue(MockResponse.Builder().code(HttpURLConnection.HTTP_CREATED).build())
        var called = false
        dav.post(sampleText.toRequestBody("text/plain".toMediaType())) { response ->
            assertEquals("POST", mockServer.takeRequest().method)
            assertEquals(response.request.url, dav.location)
            called = true
        }
        assertTrue(called)
    }

}