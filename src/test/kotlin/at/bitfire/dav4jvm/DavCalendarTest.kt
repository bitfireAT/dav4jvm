/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

package at.bitfire.dav4jvm

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant

class DavCalendarTest {

    private val httpClient = OkHttpClient.Builder()
        .followRedirects(false)
        .build()
    private val mockServer = MockWebServer()

    @Before
    fun startServer() {
        mockServer.start()
    }

    @After
    fun stopServer() {
        mockServer.shutdown()
    }


    @Test
    fun calendarQuery_formatStartEnd() {
        val cal = DavCalendar(httpClient, mockServer.url("/"))
        mockServer.enqueue(MockResponse().setResponseCode(207).setBody("<multistatus xmlns=\"DAV:\"/>"))
        cal.calendarQuery("VEVENT",
            start = Instant.ofEpochSecond(784111777),
            end = Instant.ofEpochSecond(1689324577)) { _, _ -> }
        val rq = mockServer.takeRequest()
        assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
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
                "</CAL:calendar-query>", rq.body.readUtf8())
    }

}