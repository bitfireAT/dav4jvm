/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm

import okhttp3.HttpUrl
import okhttp3.Response
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

object HttpUtils {

    /**
     * Preferred HTTP date/time format, see RFC 7231 7.1.1.1 IMF-fixdate
     */
    private const val httpDateFormatStr = "EEE, dd MMM yyyy HH:mm:ss ZZZZ"
    private val httpDateFormat = DateTimeFormatter.ofPattern(httpDateFormatStr, Locale.US)

    /**
     * Gets the resource name (the last segment of the path) from an URL.
     * Empty if the resource is the base directory.
     *
     * * `dir` for `https://example.com/dir/`
     * * `file` for `https://example.com/file`
     * * `` for `https://example.com` or  `https://example.com/`
     *
     * @return resource name
     */
    fun fileName(url: HttpUrl): String {
        val pathSegments = url.pathSegments.dropLastWhile { it == "" }
        return pathSegments.lastOrNull() ?: ""
    }

    fun listHeader(response: Response, name: String): Array<String> {
        val value = response.headers(name).joinToString(",")
        return value.split(',').filter { it.isNotEmpty() }.toTypedArray()
    }


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
     * @param dateStr date formatted in one of the three accepted formats:
     *
     *   - preferred format (`IMF-fixdate`)
     *   - obsolete RFC 850 format
     *   - ANSI C's `asctime()` format
     *
     * @return date, or null if date could not be parsed
     */
    fun parseDate(dateStr: String): ZonedDateTime? {
        val zonedFormats = arrayOf(
            // preferred format
            httpDateFormat,

            // obsolete RFC 850 format
            DateTimeFormatter.ofPattern("EEEE, dd-MMM-yy HH:mm:ss zzz", Locale.US),
        )

        // try the two formats with zone info
        for (format in zonedFormats)
            try {
                return ZonedDateTime.parse(dateStr, format)
            } catch (ignored: DateTimeParseException) {
            }

        // try ANSI C's asctime() format
        try {
            val formatC = DateTimeFormatter.ofPattern("EEE MMM ppd HH:mm:ss yyyy", Locale.US)
            val local = LocalDateTime.parse(dateStr, formatC)
            return local.atZone(ZoneOffset.UTC)
        } catch (ignored: DateTimeParseException) {
        }

        // no success in parsing
        Dav4jvm.log.warning("Couldn't parse HTTP date: $dateStr, ignoring")
        return null
    }

}