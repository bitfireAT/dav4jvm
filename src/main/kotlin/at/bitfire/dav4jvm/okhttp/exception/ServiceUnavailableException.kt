/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

package at.bitfire.dav4jvm.okhttp.exception

import at.bitfire.dav4jvm.okhttp.HttpUtils
import at.bitfire.dav4jvm.okhttp.exception.ServiceUnavailableException.Companion.DELAY_UNTIL_DEFAULT
import at.bitfire.dav4jvm.okhttp.exception.ServiceUnavailableException.Companion.DELAY_UNTIL_MAX
import at.bitfire.dav4jvm.okhttp.exception.ServiceUnavailableException.Companion.DELAY_UNTIL_MIN
import okhttp3.Response
import java.net.HttpURLConnection
import java.time.Instant
import java.util.logging.Level
import java.util.logging.Logger

class ServiceUnavailableException : HttpException {

    private val logger
        get() = Logger.getLogger(javaClass.name)

    val retryAfter: Instant?

    constructor(message: String?) : super(HttpURLConnection.HTTP_UNAVAILABLE, message) {
        retryAfter = null
    }

    constructor(response: Response) : super(response) {
        // Retry-After  = "Retry-After" ":" ( HTTP-date | delta-seconds )
        // HTTP-date    = rfc1123-date | rfc850-date | asctime-date

        var retryAfterValue: Instant? = null
        response.header("Retry-After")?.let { after ->
            retryAfterValue = HttpUtils.parseDate(after) ?:
                // not a HTTP-date, must be delta-seconds
                try {
                    val seconds = after.toLong()
                    Instant.now().plusSeconds(seconds)
                } catch (e: NumberFormatException) {
                    logger.log(Level.WARNING, "Received Retry-After which was not a HTTP-date nor delta-seconds: $after", e)
                    null
                }
        }

        retryAfter = retryAfterValue
    }


    /**
     * Returns appropriate sync retry delay in seconds, considering the servers suggestion
     * in [retryAfter] ([DELAY_UNTIL_DEFAULT] if no server suggestion).
     *
     * Takes current time into account to calculate intervals. Interval
     * will be restricted to values between [DELAY_UNTIL_MIN] and [DELAY_UNTIL_MAX].
     *
     * @param start   timestamp to calculate the delay from (default: now)
     *
     * @return until when to wait before sync can be retried
     */
    fun getDelayUntil(start: Instant = Instant.now()): Instant {
        if (retryAfter == null)
            return start.plusSeconds(DELAY_UNTIL_DEFAULT)

        // take server suggestion, but restrict to plausible min/max values
        return retryAfter.coerceIn(
            minimumValue = start.plusSeconds(DELAY_UNTIL_MIN),
            maximumValue = start.plusSeconds(DELAY_UNTIL_MAX)
        )
    }


    companion object {

        // default values for getDelayUntil
        const val DELAY_UNTIL_DEFAULT = 15 * 60L    // 15 min
        const val DELAY_UNTIL_MIN = 1 * 60L         // 1 min
        const val DELAY_UNTIL_MAX = 2 * 60 * 60L    // 2 hours

    }

}