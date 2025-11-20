/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

package at.bitfire.dav4jvm.ktor.exception

import at.bitfire.dav4jvm.HttpUtils
import at.bitfire.dav4jvm.ktor.exception.ServiceUnavailableException.Companion.DELAY_UNTIL_DEFAULT
import at.bitfire.dav4jvm.ktor.exception.ServiceUnavailableException.Companion.DELAY_UNTIL_MAX
import at.bitfire.dav4jvm.ktor.exception.ServiceUnavailableException.Companion.DELAY_UNTIL_MIN
import io.ktor.http.HttpStatusCode
import java.time.Instant

class ServiceUnavailableException internal constructor(
    responseInfo: HttpResponseInfo,

    /** unprocessed value of the `Retry-After` header */
    val retryAfter: String?
): HttpException(
    status = responseInfo.status,
    requestExcerpt = responseInfo.requestExcerpt,
    responseExcerpt = responseInfo.responseExcerpt,
    errors = responseInfo.errors
) {

    init {
        if (responseInfo.status != HttpStatusCode.ServiceUnavailable)
            throw IllegalArgumentException("Status must be ${HttpStatusCode.ServiceUnavailable}")
    }

    /**
     * absolute time of [retryAfter] (if available)
     */
    val retryAfterAbs: Instant? =
        if (retryAfter != null) {
            // Retry-After  = "Retry-After" ":" ( HTTP-date | delta-seconds )
            // HTTP-date    = rfc1123-date | rfc850-date | asctime-date
            HttpUtils.parseDate(retryAfter)     // parse as HTTP-date, if possible
                ?: parseAsSeconds(retryAfter)           // not a HTTP-date, must be delta-seconds
        } else
            null

    private fun parseAsSeconds(retryAfter: String): Instant? {
        return try {
            val seconds = retryAfter.toLong()
            Instant.now().plusSeconds(seconds)
        } catch (_: NumberFormatException) {
            null
        }
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
        if (retryAfterAbs == null)
            return start.plusSeconds(DELAY_UNTIL_DEFAULT)

        // take server suggestion, but restrict to plausible min/max values
        return retryAfterAbs.coerceIn(
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