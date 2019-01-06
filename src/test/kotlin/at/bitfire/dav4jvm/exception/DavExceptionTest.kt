/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm.exception

import at.bitfire.dav4jvm.DavResource
import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.XmlUtils
import at.bitfire.dav4jvm.property.ResourceType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

class DavExceptionTest {

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
     * Test a large HTML response which has a multi-octet UTF-8 character
     * exactly at the cut-off position.
     */
    @Test
    fun testLargeTextError() {
        val url = sampleUrl()
        val dav = DavResource(httpClient, url)

        val builder = StringBuilder()
        builder.append(CharArray(DavException.MAX_EXCERPT_SIZE-1) { '*' })
        builder.append("\u03C0")    // Pi
        val body = builder.toString()

        mockServer.enqueue(MockResponse()
                .setResponseCode(404)
                .setHeader("Content-Type", "text/html")
                .setBody(body))
        try {
            dav.propfind(0, ResourceType.NAME) { _, _ -> }
            fail("Expected HttpException")
        } catch (e: HttpException) {
            assertEquals(e.code, 404)
            assertTrue(e.errors.isEmpty())
            assertEquals(
                    body.substring(0, DavException.MAX_EXCERPT_SIZE-1),
                    e.responseBody!!.substring(0, DavException.MAX_EXCERPT_SIZE-1)
            )
        }
    }

    @Test
    fun testNonTextError() {
        val url = sampleUrl()
        val dav = DavResource(httpClient, url)

        mockServer.enqueue(MockResponse()
                .setResponseCode(403)
                .setHeader("Content-Type", "application/octet-stream")
                .setBody("12345"))
        try {
            dav.propfind(0, ResourceType.NAME) { _, _ -> }
            fail("Expected HttpException")
        } catch (e: HttpException) {
            assertEquals(e.code, 403)
            assertTrue(e.errors.isEmpty())
            assertNull(e.responseBody)
        }
    }

    fun testSerialization() {
        val url = sampleUrl()
        val dav = DavResource(httpClient, url)

        mockServer.enqueue(MockResponse()
                .setResponseCode(500)
                .setBody("12345"))
        try {
            dav.propfind(0, ResourceType.NAME) { _, _ -> }
            fail("Expected DavException")
        } catch (e: DavException) {
            val baos = ByteArrayOutputStream()
            val oos = ObjectOutputStream(baos)
            oos.writeObject(e)

            val ois = ObjectInputStream(ByteArrayInputStream(baos.toByteArray()))
            val e2 = ois.readObject() as HttpException
            assertEquals(500, e2.code)
            assertTrue(e2.responseBody!!.contains("12345"))
        }
    }

    /**
     * Test precondition XML element (sample from RFC 4918 16)
     */
    @Test
    fun testXmlError() {
        val url = sampleUrl()
        val dav = DavResource(httpClient, url)

        val body = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
                "<D:error xmlns:D=\"DAV:\">\n" +
                "  <D:lock-token-submitted>\n" +
                "    <D:href>/workspace/webdav/</D:href>\n" +
                "  </D:lock-token-submitted>\n" +
                "</D:error>\n"
        mockServer.enqueue(MockResponse()
                .setResponseCode(423)
                .setHeader("Content-Type", "application/xml; charset=\"utf-8\"")
                .setBody(body))
        try {
            dav.propfind(0, ResourceType.NAME) { _, _ -> }
            fail("Expected HttpException")
        } catch (e: HttpException) {
            assertEquals(e.code, 423)
            assertTrue(e.errors.any { it.name == Property.Name(XmlUtils.NS_WEBDAV, "lock-token-submitted") })
            assertEquals(body, e.responseBody)
        }
    }

}