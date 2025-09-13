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

import at.bitfire.dav4jvm.ktor.HttpUtils.httpDateFormat
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
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

    val INVALID_STATUS = HttpStatusCode( 500, "Invalid status line")


    private val logger
        get() = Logger.getLogger(javaClass.name)


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
    fun fileName(url: Url): String = url.segments.lastOrNull() ?: ""  // segments excludes empty segments

    /**
     * Gets all values of a header that is defined as a list [RFC 9110 5.6.1],
     * regardless of they're sent as one line or as multiple lines.
     *
     * For instance, regardless of whether a server sends:
     *
     * ```
     * DAV: 1
     * DAV: 2
     * ```
     *
     * or
     *
     * ```
     * DAV: 1, 2
     * ```
     *
     * this method would return `arrayOf("1","2")` for the `DAV` header.
     *
     * @param response  the HTTP response to evaluate
     * @param name      header name (for instance: `DAV`)
     *
     * @return all values for the given header name
     */
    fun listHeader(response: HttpResponse, name: String): Array<String> {
        val value = response.headers.getAll(name)?.joinToString(",")
        return value?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toTypedArray() ?: emptyArray()
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

    /**
     * Parses an HTTP status line.
     *
     * It supports both full status lines like "HTTP/1.1 200 OK"
     * and partial ones like "200 OK" or just "200".
     *
     * If the status line cannot be parsed, an [HttpStatusCode] object
     * with the value 500 "Invalid status line" is returned.
     *
     * @param statusText the status line to parse.
     * @return an [HttpStatusCode] object representing the parsed status.
     */
    fun parseStatusLine(statusText: String): HttpStatusCode {


        val parts = statusText.split(" ", limit = 3)
        return if (parts.size >= 2 && parts[0].startsWith("HTTP/")) { // Full status line like "HTTP/1.1 200 OK"
            val statusCode = parts[1].toIntOrNull()
            val description = if (parts.size > 2) parts[2] else ""
            if (statusCode != null && statusCode in 1..999) {
                HttpStatusCode(statusCode, description)
            } else {
                INVALID_STATUS
            }
        } else if (parts.isNotEmpty()) { // Potentially just "200 OK" or "200"
            val statusCode = parts[0].toIntOrNull()
            val description = if (parts.size > 1) parts.drop(1).joinToString(" ") else HttpStatusCode.allStatusCodes.find { it.value == statusCode }?.description ?: ""
            if (statusCode != null && statusCode in 1..999) {
                HttpStatusCode(statusCode, description)
            } else {
                INVALID_STATUS
            }
        } else {
            INVALID_STATUS
        }
    }

}