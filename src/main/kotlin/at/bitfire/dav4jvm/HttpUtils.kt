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

import io.ktor.http.fromHttpToGmtDate
import io.ktor.http.toHttpDate
import io.ktor.util.date.GMTDate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoField
import java.util.Locale
import java.util.logging.Logger

/**
 * Generic HTTP utilities, like processing HTTP dates
 */
object HttpUtils {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    /**
     * Formats a date for use in HTTP headers (RFC 7231 IMF-fixdate).
     *
     * @param date date to be formatted
     * @return date in HTTP-date format
     */
    fun formatDate(date: Instant): String =
        GMTDate(date.toEpochMilli()).toHttpDate()

    /**
     * Parses an HTTP-date (RFC 9110 section 5.6.7) in any of the three HTTP-date formats.
     *
     * @param dateStr date-time to parse
     * @return date-time, or null if date could not be parsed
     */
    fun parseDate(dateStr: String): Instant? {
        val str = dateStr.trim()
        val result = try {
            // handles IMF-fixdate and some non-standard formats
            Instant.ofEpochMilli(str.fromHttpToGmtDate().timestamp)
        } catch (_: Exception) {
            fallbackDateFormats.firstNotNullOfOrNull { format ->
                try {
                    // parse with format, generate an Instant from it
                    format.parse(str, Instant::from)
                } catch (_: DateTimeParseException) {
                    null
                }
            }
        }
        if (result == null)
            logger.warning("Couldn't parse HTTP date: $dateStr, ignoring")
        return result
    }

    // formats currently not covered by Ktor's fromHttpToGmtDate() (as of Ktor 3.6.1), tried in order
    private val fallbackDateFormats = listOf(
        // not an HTTP-date; RFC 5322 date with numeric offset, accepted for compatibility
        // since some servers send this format: Sun, 06 Nov 1994 08:49:37 +0000
        DateTimeFormatter.RFC_1123_DATE_TIME,

        // RFC 9110 5.6.7 obsolete RFC 850 format: Sunday, 06-Nov-94 08:49:37 GMT
        DateTimeFormatterBuilder()
            .appendPattern("EEEE, dd-MMM-")
            // 2-digit year more than 50 years in the future means the most recent past year
            .appendValueReduced(ChronoField.YEAR, 2, 2, LocalDate.now(ZoneOffset.UTC).minusYears(49))
            .appendPattern(" HH:mm:ss 'GMT'")
            .toFormatter(Locale.US)
            .withZone(ZoneOffset.UTC),

        // RFC 9110 5.6.7 obsolete asctime() format, assumed UTC: Sun Nov  6 08:49:37 1994
        DateTimeFormatter.ofPattern("EEE MMM ppd HH:mm:ss yyyy", Locale.US).withZone(ZoneOffset.UTC)
    )

}