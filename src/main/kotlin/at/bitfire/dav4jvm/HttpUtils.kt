/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm

import okhttp3.HttpUrl
import okhttp3.Response
import org.apache.commons.lang3.time.DateUtils
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.*


object HttpUtils {

    const val httpDateFormatStr = "EEE, dd MMM yyyy HH:mm:ss 'GMT'"
    val httpDateFormat = SimpleDateFormat(httpDateFormatStr)

    /**
     * Gets the resource name (the last segment of the path) from an URL.
     *
     * @return resource name or `` (empty string) if the URL ends with a slash
     *         (i.e. the resource is a collection).
     */
    fun fileName(url: HttpUrl): String {
        val pathSegments = url.pathSegments
        return pathSegments[pathSegments.size - 1]
    }

    fun listHeader(response: Response, name: String): Array<String> {
        val value = response.headers(name).joinToString(",")
        return value.split(',').filter { it.isNotEmpty() }.toTypedArray()
    }


    /**
     * Formats a date for use in HTTP headers using [httpDateFormat].
     *
     * @param date date to be formatted
     * @return date in HTTP-date format
     */
    fun formatDate(date: Date): String = httpDateFormat.format(date)

    /**
     * Parses a HTTP-date.
     *
     * @param dateStr date with format specified by RFC 7231 section 7.1.1.1
     * or in one of the obsolete formats (copied from okhttp internal date-parsing class)
     *
     * @return date, or null if date could not be parsed
     */
    fun parseDate(dateStr: String) = try {
        DateUtils.parseDate(dateStr,
                httpDateFormatStr,
                "EEE, dd MMM yyyy HH:mm:ss zzz", // RFC 822, updated by RFC 1123 with any TZ
                "EEEE, dd-MMM-yy HH:mm:ss zzz", // RFC 850, obsoleted by RFC 1036 with any TZ.
                "EEE MMM d HH:mm:ss yyyy", // ANSI C's asctime() format
                // Alternative formats.
                "EEE, dd-MMM-yyyy HH:mm:ss z",
                "EEE, dd-MMM-yyyy HH-mm-ss z",
                "EEE, dd MMM yy HH:mm:ss z",
                "EEE dd-MMM-yyyy HH:mm:ss z",
                "EEE dd MMM yyyy HH:mm:ss z",
                "EEE dd-MMM-yyyy HH-mm-ss z",
                "EEE dd-MMM-yy HH:mm:ss z",
                "EEE dd MMM yy HH:mm:ss z",
                "EEE,dd-MMM-yy HH:mm:ss z",
                "EEE,dd-MMM-yyyy HH:mm:ss z",
                "EEE, dd-MM-yyyy HH:mm:ss z",
                /* RI bug 6641315 claims a cookie of this format was once served by www.yahoo.com */
                "EEE MMM d yyyy HH:mm:ss z"
        )
    } catch (e: ParseException) {
        Dav4jvm.log.warning("Couldn't parse date: $dateStr, ignoring")
        null
    }

}