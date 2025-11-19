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

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import io.ktor.http.withCharset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class DavCalendarTest {

    @Test
    fun calendarQuery_formatStartEnd() = runTest {
        val mockEngine = MockEngine { request ->
            respond(
                content = "<multistatus xmlns=\"DAV:\"/>",
                status = HttpStatusCode.MultiStatus,  // 207
                headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Xml.withCharset(Charsets.UTF_8).toString())
            )
        }
        val httpClient = HttpClient(mockEngine) { followRedirects = false }
        val cal = DavCalendar(httpClient, Url("/"))

        cal.calendarQuery(
            "VEVENT",
            start = Instant.ofEpochSecond(784111777),
            end = Instant.ofEpochSecond(1689324577)
        ) { _, _ -> }

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
            mockEngine.requestHistory.last().body.toByteArray().toString(Charsets.UTF_8)
        )
    }

}