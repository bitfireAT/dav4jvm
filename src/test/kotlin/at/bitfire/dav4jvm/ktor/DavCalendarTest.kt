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

import at.bitfire.dav4jvm.property.webdav.WebDAV
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import io.ktor.http.withCharset
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class DavCalendarTest {

    private val sampleUrl = Url("http://127.0.0.1/dav/")

    private fun minimalMultiStatus() = MockEngine { _ ->
        respond(
            "<?xml version=\"1.0\" encoding=\"utf-8\"?><multistatus xmlns=\"DAV:\"/>",
            HttpStatusCode.MultiStatus,
            headersOf(HttpHeaders.ContentType, ContentType.Text.Xml.withCharset(Charsets.UTF_8).toString())
        )
    }

    private fun davCalendar(engine: MockEngine) = DavCalendar(HttpClient(engine), sampleUrl)

    private suspend fun requestBody(engine: MockEngine) =
        engine.requestHistory.last().body.toByteArray().toString(Charsets.UTF_8)


    @Test
    fun `calendarQuery formats start and end times as UTC`() = runTest {
        val engine = minimalMultiStatus()
        davCalendar(engine).calendarQuery(
            "VEVENT",
            start = Instant.ofEpochSecond(784111777),
            end = Instant.ofEpochSecond(1689324577)
        ).toList()
        assertEquals(
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
                    "<CAL:calendar-query xmlns=\"DAV:\" xmlns:CAL=\"urn:ietf:params:xml:ns:caldav\">" +
                    "<prop>" +
                    "<getetag />" +
                    "</prop>" +
                    "<CAL:filter>" +
                    "<CAL:comp-filter name=\"VCALENDAR\">" +
                    "<CAL:comp-filter name=\"VEVENT\">" +
                    "<CAL:time-range start=\"19941106T084937Z\" end=\"20230714T084937Z\" />" +
                    "</CAL:comp-filter>" +
                    "</CAL:comp-filter>" +
                    "</CAL:filter>" +
                    "</CAL:calendar-query>",
            requestBody(engine)
        )
    }

    @Test
    fun `calendarQuery without time range omits time-range element`() = runTest {
        val engine = minimalMultiStatus()
        davCalendar(engine).calendarQuery("VEVENT", start = null, end = null).toList()
        assertFalse(requestBody(engine).contains("<CAL:time-range"))
    }

    @Test
    fun `calendarQuery custom component name used in comp-filter`() = runTest {
        val engine = minimalMultiStatus()
        davCalendar(engine).calendarQuery("VTODO", start = null, end = null).toList()
        assertTrue(requestBody(engine).contains("name=\"VTODO\""))
    }

    @Test
    fun `calendarQuery custom props included in request`() = runTest {
        val engine = minimalMultiStatus()
        davCalendar(engine).calendarQuery(
            "VEVENT", start = null, end = null,
            props = setOf(WebDAV.GetETag, WebDAV.DisplayName)
        ).toList()
        val body = requestBody(engine)
        assertTrue(body.contains("<getetag />"))
        assertTrue(body.contains("<displayname />"))
    }

    @Test
    fun `calendarQuery sends proper request`() = runTest {
        val engine = minimalMultiStatus()
        davCalendar(engine).calendarQuery("VEVENT", start = null, end = null).toList()
        with(engine.requestHistory.last()) {
            assertEquals(HttpMethod.parse("REPORT"), method)
            assertEquals("1", headers[HttpHeaders.Depth])
            assertEquals(DavResource.MIME_XML_UTF8, body.contentType)
            assertEquals(listOf("application/xml", "text/xml"), headers.getAll(HttpHeaders.Accept))
        }
    }

    @Test
    fun `multiget sends proper request`() = runTest {
        val engine = minimalMultiStatus()
        davCalendar(engine).multiget(listOf(sampleUrl)).toList()
        with(engine.requestHistory.last()) {
            assertEquals(HttpMethod.parse("REPORT"), method)
            assertNull(headers[HttpHeaders.Depth])
            assertEquals(DavResource.MIME_XML_UTF8, body.contentType)
            assertEquals(listOf("application/xml", "text/xml"), headers.getAll(HttpHeaders.Accept))
        }
    }

    @Test
    fun `multiget request body contains hrefs and calendar-data`() = runTest {
        val engine = minimalMultiStatus()
        val url1 = Url("http://127.0.0.1/dav/cal1.ics")
        val url2 = Url("http://127.0.0.1/dav/cal2.ics")
        davCalendar(engine).multiget(listOf(url1, url2)).toList()
        val body = requestBody(engine)
        assertTrue(body.contains("CAL:calendar-multiget"))
        assertTrue(body.contains("<href>/dav/cal1.ics</href>"))
        assertTrue(body.contains("<href>/dav/cal2.ics</href>"))
        assertTrue(body.contains("<CAL:calendar-data />"))
        assertFalse(body.contains("content-type="))
    }

    @Test
    fun `multiget with contentType adds attributes to calendar-data`() = runTest {
        val engine = minimalMultiStatus()
        davCalendar(engine).multiget(listOf(sampleUrl), contentType = "text/calendar", version = "2.0").toList()
        val body = requestBody(engine)
        assertTrue(body.contains("content-type=\"text/calendar\""))
        assertTrue(body.contains("version=\"2.0\""))
    }

}
