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

import io.ktor.http.Url
import io.ktor.http.fromHttpToGmtDate
import io.ktor.http.toHttpDate
import io.ktor.util.date.GMTDate
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.time.Instant
import java.util.logging.Logger

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
     * Parses an HTTP-date according to RFC 7231 section 7.1.1.1.
     *
     * @param dateStr date-time formatted in one of the three accepted formats:
     *
     *   - preferred format (`IMF-fixdate`)
     *   - obsolete RFC 850 format
     *   - ANSI C's `asctime()` format
     *
     * @return date-time, or null if date could not be parsed
     */
    fun parseDate(dateStr: String): Instant? = try {
        val ts = dateStr.fromHttpToGmtDate().timestamp
        Instant.ofEpochMilli(ts)
    } catch (_: Exception) {
        logger.warning("Couldn't parse HTTP date: $dateStr, ignoring")
        null
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