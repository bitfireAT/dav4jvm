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

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.logging.Logger

class HttpUtilsTest {

    val sampleUrl = Url("http://127.0.0.1")
    private val multipleHeaderValues = "  1,  2 ,3,hyperactive-access"
    private val singleHeaderValue = "other"

    private fun getMockEngineWithDAVHeaderValues(vararg headerValues: String): HttpClient {
        val mockEngine = MockEngine { request ->
            respond("",HttpStatusCode.OK, HeadersBuilder().apply {
                headerValues.forEach { headerValue ->
                    append("DAV", headerValue)
                }
            }.build())
        }
        return HttpClient(mockEngine)
    }


    @Test
    fun fileName() {
        assertEquals("", HttpUtils.fileName(Url("https://example.com")))
        assertEquals("", HttpUtils.fileName(Url("https://example.com/")))
        assertEquals("file1", HttpUtils.fileName(Url("https://example.com/file1")))
        assertEquals("dir1", HttpUtils.fileName(Url("https://example.com/dir1/")))
        assertEquals("file2", HttpUtils.fileName(Url("https://example.com/dir1/file2")))
        assertEquals("dir2", HttpUtils.fileName(Url("https://example.com/dir1/dir2/")))
    }

    @Test
    fun formatDate() {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("GMT"))
        cal.set(2023, 4, 11, 17, 26, 35)
        cal.timeZone = TimeZone.getTimeZone("UTC")
        assertEquals("Sun, 06 Nov 1994 08:49:37 GMT", HttpUtils.formatDate(
            ZonedDateTime.of(
                LocalDate.of(1994, 11, 6),
                LocalTime.of(8, 49, 37),
                ZoneOffset.UTC
            ).toInstant()
        ))
    }


    @Test
    fun parseDate_IMF_FixDate() {
        // RFC 7231 IMF-fixdate (preferred format)
        assertEquals(Instant.ofEpochSecond(784111777), HttpUtils.parseDate("Sun, 06 Nov 1994 08:49:37 GMT"))
    }

    @Test
    fun parseDate_RFC850_1994() {
        // obsolete RFC 850 format – fails when run after year 2000 because 06 Nov 2094 (!) is not a Sunday
        assertNull(HttpUtils.parseDate("Sun, 06-Nov-94 08:49:37 GMT"))
    }

    @Test
    fun parseDate_RFC850_2004_CEST() {
        // obsolete RFC 850 format with European time zone
        assertEquals(Instant.ofEpochSecond(1689317377), HttpUtils.parseDate("Friday, 14-Jul-23 08:49:37 CEST"))
    }

    @Test
    fun parseDate_RFC850_2004_GMT() {
        // obsolete RFC 850 format
        assertEquals(Instant.ofEpochSecond(1689324577), HttpUtils.parseDate("Friday, 14-Jul-23 08:49:37 GMT"))
    }

    @Test
    fun parseDate_ANSI_C() {
        // ANSI C's asctime() format
        val logger = Logger.getLogger(javaClass.name)
        logger.info("Expected date: " + DateTimeFormatter.ofPattern("EEE MMM ppd HH:mm:ss yyyy", Locale.US).format(ZonedDateTime.now()))

        assertEquals(Instant.ofEpochSecond(784111777), HttpUtils.parseDate("Sun Nov  6 08:49:37 1994"))
    }


    @Test
    fun `listHeader with a single header value`() {
        // Verify that when a header name has a single value, it's returned as a single-element array.
        runBlocking {
            val httpClient = getMockEngineWithDAVHeaderValues(singleHeaderValue)
            val headersArray = HttpUtils.listHeader(httpClient.get(sampleUrl), "DAV")
            assertEquals(singleHeaderValue, headersArray[0])
            assertEquals(1, headersArray.size)
        }
    }

    @Test
    fun `listHeader with multiple comma separated header values`() {
        // Verify that when a header name has multiple comma-separated values, they are correctly split into an array.
        runBlocking {
            val httpClient = getMockEngineWithDAVHeaderValues(multipleHeaderValues)
            val headersArray = HttpUtils.listHeader(httpClient.get(sampleUrl), "DAV")
            assertEquals(4, headersArray.size)
            assertEquals("1", headersArray[0])
            assertEquals("2", headersArray[1])
            assertEquals("3", headersArray[2])
            assertEquals("hyperactive-access", headersArray[3])
        }
    }

    @Test
    fun `listHeader with multiple distinct header entries for the same name`() {
        // Verify that if the same header name appears multiple times (e.g., 'Set-Cookie'), all values are joined by a comma and then split correctly.
        runBlocking {
            val httpClient = getMockEngineWithDAVHeaderValues(multipleHeaderValues, singleHeaderValue)
            val headersArray = HttpUtils.listHeader(httpClient.get(sampleUrl), "DAV")
            assertEquals(5, headersArray.size)
            assertEquals("1", headersArray[0])
            assertEquals("2", headersArray[1])
            assertEquals("3", headersArray[2])
            assertEquals("hyperactive-access", headersArray[3])
            assertEquals("other", headersArray[4])
        }
    }

    @Test
    fun `listHeader with a header name that does not exist`() {
        // Verify that when a requested header name is not present in the response, an empty array is returned.
        runBlocking {
            val httpClient = getMockEngineWithDAVHeaderValues(multipleHeaderValues, singleHeaderValue)
            val headersArray = HttpUtils.listHeader(httpClient.get(sampleUrl), "other")
            assertEquals(0, headersArray.size)
        }
    }

    @Test
    fun `listHeader with an empty header value`() {
        // Verify that if a header exists but its value is an empty string, an empty array is returned (due to filter { it.isNotEmpty() }).
        runBlocking {
            val httpClient = getMockEngineWithDAVHeaderValues("", "", "")
            val headersArray = HttpUtils.listHeader(httpClient.get(sampleUrl), "DAV")
            assertEquals(0, headersArray.size)
        }
    }

    @Test
    fun `listHeader with a header value containing only commas`() {
        // Verify that if a header value consists only of commas (e.g., ',,,') an empty array is returned.
        runBlocking {
            val httpClient = getMockEngineWithDAVHeaderValues(",", ",", ",")
            val headersArray = HttpUtils.listHeader(httpClient.get(sampleUrl), "DAV")
            assertEquals(0, headersArray.size)
        }
    }


    @Test
    fun `listHeader with header values that are themselves empty after splitting`() {
        // Verify that if a header value is like 'value1,,value2', the empty string between commas is filtered out, resulting in ['value1', 'value2'].
        runBlocking {
            val httpClient = getMockEngineWithDAVHeaderValues("   ", singleHeaderValue, ", ")
            val headersArray = HttpUtils.listHeader(httpClient.get(sampleUrl), "DAV")
            assertEquals(1, headersArray.size)
            assertEquals(singleHeaderValue, headersArray[0])
        }
    }

    @Test
    fun `listHeader with a case insensitive header name`() {
        // HTTP header names are case-insensitive. Verify that `response.headers.getAll(name)` correctly retrieves the header regardless of the casing used for `name` (e.g., 'Content-Type' vs 'content-type').
        runBlocking {
            val httpClient = getMockEngineWithDAVHeaderValues(singleHeaderValue)
            val headersArray = HttpUtils.listHeader(httpClient.get(sampleUrl), "dav")
            assertEquals(singleHeaderValue, headersArray[0])
            assertEquals(1, headersArray.size)
        }
    }

    @Test
    fun `listHeader with an empty string as header name`() {
        // Test what happens if an empty string is passed as the header name. This depends on how `response.headers.getAll` handles empty keys.
        runBlocking {
            val httpClient = getMockEngineWithDAVHeaderValues(singleHeaderValue)
            val headersArray = HttpUtils.listHeader(httpClient.get(sampleUrl), "")
            assertEquals(0, headersArray.size)
        }
    }


}