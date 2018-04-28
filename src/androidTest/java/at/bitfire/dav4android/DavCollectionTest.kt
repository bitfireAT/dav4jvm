/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.dav4android

import at.bitfire.dav4android.property.GetETag
import at.bitfire.dav4android.property.SyncToken
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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


    @Test
    fun testSyncCollectionReport() {
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
        collection.reportChanges(null, false, null, GetETag.NAME)

        assertEquals(2, collection.members.size)
        val members = collection.members.iterator()
        val member1 = members.next()
        assertEquals(sampleUrl().newBuilder().addPathSegment("test.doc").build(), member1.location)
        assertEquals("00001-abcd1", member1.properties[GetETag::class.java]!!.eTag)

        val member2 = members.next()
        assertEquals(sampleUrl().newBuilder().addPathSegment("vcard.vcf").build(), member2.location)
        assertEquals("00002-abcd1", member2.properties[GetETag::class.java]!!.eTag)

        assertEquals(1, collection.removedMembers.size)
        val removedMember = collection.removedMembers.first()
        assertEquals(sampleUrl().newBuilder().addPathSegment("removed.txt").build(), removedMember.location)

        assertTrue(collection.furtherResults)
        assertEquals("http://example.com/ns/sync/1233", collection.properties[SyncToken::class.java]!!.token)
    }

}