/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.apache.commons.lang3.time.TimeZones
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

class HttpUtilsTest {

    @Test
    fun fileName() {
        assertEquals("", HttpUtils.fileName("https://example.com".toHttpUrl()))
        assertEquals("", HttpUtils.fileName("https://example.com/".toHttpUrl()))
        assertEquals("file1", HttpUtils.fileName("https://example.com/file1".toHttpUrl()))
        assertEquals("dir1", HttpUtils.fileName("https://example.com/dir1/".toHttpUrl()))
        assertEquals("file2", HttpUtils.fileName("https://example.com/dir1/file2".toHttpUrl()))
        assertEquals("dir2", HttpUtils.fileName("https://example.com/dir1/dir2/".toHttpUrl()))
    }

    @Test
    fun formatDate() {
        val cal = Calendar.getInstance(TimeZone.getTimeZone(TimeZones.GMT_ID))
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
        Dav4jvm.log.info("Expected date: " + DateTimeFormatter.ofPattern("EEE MMM ppd HH:mm:ss yyyy", Locale.US).format(ZonedDateTime.now()))
        assertEquals(Instant.ofEpochSecond(784111777), HttpUtils.parseDate("Sun Nov  6 08:49:37 1994"))
    }

}