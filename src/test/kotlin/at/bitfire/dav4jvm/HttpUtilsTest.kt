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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.ZonedDateTime

class HttpUtilsTest {

    @Test
    fun `formatDate() formats as IMF-fixdate`() {
        val date = ZonedDateTime.of(
            LocalDate.of(1994, 11, 6),
            LocalTime.of(8, 49, 37),
            ZoneOffset.UTC
        ).toInstant()

        val result = HttpUtils.formatDate(date)

        assertEquals("Sun, 06 Nov 1994 08:49:37 GMT", result)
    }


    @Test
    fun `formatDate() uses GMT time zone`() {
        // See https://github.com/bitfireAT/dav4jvm/issues/22
        assertTrue(HttpUtils.formatDate(Instant.EPOCH).endsWith(" GMT"))
    }

    @Test
    fun `parseDate() with IMF-fixdate`() {
        // RFC 7231 IMF-fixdate (preferred format)
        assertEquals(Instant.ofEpochSecond(784111777), HttpUtils.parseDate("Sun, 06 Nov 1994 08:49:37 GMT"))
    }

    @Test
    fun `parseDate() with IMF-fixdate treats GMT as UTC`() {
        // See https://github.com/bitfireAT/dav4jvm/issues/22
        assertEquals(Instant.parse("2026-05-04T22:51:02Z"), HttpUtils.parseDate("Mon, 04 May 2026 22:51:02 GMT"))
    }

    @Test
    fun `parseDate() with non-standard date supported by Ktor`() {
        val result = HttpUtils.parseDate("Sun, 06-Nov-1994 08:49:37 GMT")

        assertEquals(Instant.ofEpochSecond(784111777), result)
    }

    @Test
    fun `parseDate() with RFC 5322 date and zero offset`() {
        val result = HttpUtils.parseDate("Mon, 09 Nov 2026 09:07:23 +0000")

        assertEquals(Instant.parse("2026-11-09T09:07:23Z"), result)
    }

    @Test
    fun `parseDate() with RFC 5322 date and non-zero offset`() {
        val result = HttpUtils.parseDate("Mon, 09 Nov 2026 10:07:23 +0100")

        assertEquals(Instant.parse("2026-11-09T09:07:23Z"), result)
    }

    @Test
    fun `parseDate() with RFC 850 date`() {
        val result = HttpUtils.parseDate("Sunday, 06-Nov-94 08:49:37 GMT")

        assertEquals(Instant.ofEpochSecond(784111777), result)
    }

    @Test
    fun `parseDate() with RFC 850 date and 2-digit year more than 50 years ahead uses past century`() {
        val result = HttpUtils.parseDate("Sunday, 06-Nov-94 08:49:37 GMT")

        assertEquals(1994, result!!.atZone(ZoneOffset.UTC).year)
    }

    @Test
    fun `parseDate() with RFC 850 date and 2-digit year within 50 years uses current century`() {
        val result = HttpUtils.parseDate("Monday, 04-May-26 22:51:02 GMT")

        assertEquals(Instant.parse("2026-05-04T22:51:02Z"), result)
    }

    @Test
    fun `parseDate() with asctime date`() {
        val result = HttpUtils.parseDate("Sun Nov  6 08:49:37 1994")

        assertEquals(Instant.ofEpochSecond(784111777), result)
    }

    @Test
    fun `parseDate() with asctime date and 2-digit day`() {
        val result = HttpUtils.parseDate("Thu May 14 22:51:02 2026")

        assertEquals(Instant.parse("2026-05-14T22:51:02Z"), result)
    }

    @Test
    fun `parseDate() with invalid date returns null`() {
        val result = HttpUtils.parseDate("not a date")

        assertNull(result)
    }

}