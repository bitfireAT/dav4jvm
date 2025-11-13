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

import at.bitfire.dav4jvm.HttpUtils.httpDateFormat
import io.ktor.http.Url
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import java.util.logging.Logger

object HttpUtils {

    /**
     * Preferred HTTP date/time format, see RFC 7231 7.1.1.1 IMF-fixdate
     */
    private const val httpDateFormatStr = "EEE, dd MMM yyyy HH:mm:ss ZZZZ"
    private val httpDateFormat = DateTimeFormatter.ofPattern(httpDateFormatStr, Locale.US)

    private val logger
        get() = Logger.getLogger(javaClass.name)

    /**
     * Formats a date for use in HTTP headers using [httpDateFormat].
     *
     * @param date date to be formatted
     *
     * @return date in HTTP-date format
     */
    fun formatDate(date: Instant): String =
        ZonedDateTime.ofInstant(date, ZoneOffset.UTC).format(httpDateFormat)

    /**
     * Parses a HTTP-date according to RFC 7231 section 7.1.1.1.
     *
     * @param dateStr date-time formatted in one of the three accepted formats:
     *
     *   - preferred format (`IMF-fixdate`)
     *   - obsolete RFC 850 format
     *   - ANSI C's `asctime()` format
     *
     * @return date-time, or null if date could not be parsed
     */
    fun parseDate(dateStr: String): Instant? {
        val zonedFormats = arrayOf(
            // preferred format
            httpDateFormat,

            // obsolete RFC 850 format
            DateTimeFormatter.ofPattern("EEEE, dd-MMM-yy HH:mm:ss zzz", Locale.US),
        )

        // try the two formats with zone info
        for (format in zonedFormats)
            try {
                return ZonedDateTime.parse(dateStr, format).toInstant()
            } catch (ignored: DateTimeParseException) {
            }

        // try ANSI C's asctime() format
        try {
            val formatC = DateTimeFormatter.ofPattern("EEE MMM ppd HH:mm:ss yyyy", Locale.US)
            val local = LocalDateTime.parse(dateStr, formatC)
            return local.atZone(ZoneOffset.UTC).toInstant()
        } catch (ignored: DateTimeParseException) {
        }

        // no success in parsing
        logger.warning("Couldn't parse HTTP date: $dateStr, ignoring")
        return null
    }


    // for migration between Ktor and okhttp

    /**
     * Converts an okhttp [HttpUrl] to a Ktor [Url].
     */
    fun HttpUrl.toKtorUrl() = Url(toString())

    /**
     * Converts a Ktor [Url] to an okhttp [HttpUrl].
     */
    fun Url.toHttpUrl() = toString().toHttpUrl()

}