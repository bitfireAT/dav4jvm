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

import at.bitfire.dav4jvm.HttpUtils.toHttpUrl
import at.bitfire.dav4jvm.HttpUtils.toKtorUrl
import io.ktor.http.Url
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.Calendar
import java.util.TimeZone

class HttpUtilsTest {

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
    fun formatDate_timezone_is_GMT() {
        // See https://github.com/bitfireAT/dav4jvm/issues/22
        assertTrue(HttpUtils.formatDate(Instant.EPOCH).endsWith(" GMT"))
    }

    @Test
    fun parseDate_IMF_FixDate() {
        // RFC 7231 IMF-fixdate (preferred format)
        assertEquals(Instant.ofEpochSecond(784111777), HttpUtils.parseDate("Sun, 06 Nov 1994 08:49:37 GMT"))
    }

    @Test
    fun parseDate_IMF_FixDate_GMT() {
        // See https://github.com/bitfireAT/dav4jvm/issues/22
        assertEquals(Instant.parse("2026-05-04T22:51:02Z"), HttpUtils.parseDate("Mon, 04 May 2026 22:51:02 GMT"))
    }

    @Test
    fun parseDate_RFC850_1994() {
        // obsolete RFC 850 format – 2-digit year cannot be parsed, returns null
        assertNull(HttpUtils.parseDate("Sun, 06-Nov-94 08:49:37 GMT"))
    }


    @Test
    fun testHttpUrl_toKtorUrl() {
        assertEquals(Url("https://example.com:123/path"), "https://example.com:123/path".toHttpUrl().toKtorUrl())
    }

    @Test
    fun testUrl_ToHttpUrl() {
        assertEquals("https://example.com:123/path".toHttpUrl(), Url("https://example.com:123/path").toHttpUrl())
    }

}