/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm.exception

import at.bitfire.dav4jvm.Dav4jvm
import at.bitfire.dav4jvm.HttpUtils
import io.ktor.client.statement.*
import korlibs.time.DateTime
import korlibs.time.DateTimeTz
import korlibs.time.seconds

class ServiceUnavailableException// not a HTTP-date, must be delta-seconds// Retry-After  = "Retry-After" ":" ( HTTP-date | delta-seconds )
// HTTP-date    = rfc1123-date | rfc850-date | asctime-date
internal constructor(response: HttpResponse, exceptionData: ExceptionData) :
    HttpException(response.status, exceptionData) {

    companion object {
        suspend operator fun invoke(httpResponse: HttpResponse) =
            ServiceUnavailableException(httpResponse, createExceptionData(httpResponse))
    }

    var retryAfter: DateTimeTz? = null


    init {
        response.headers["Retry-After"]?.let { after ->
            retryAfter = HttpUtils.parseDate(after) ?:
                    // not a HTTP-date, must be delta-seconds
                    try {
                        val seconds = after.toInt()

                        DateTime.now().local + seconds.seconds

                    } catch (ignored: NumberFormatException) {
                        Dav4jvm.log.warn("Received Retry-After which was not a HTTP-date nor delta-seconds: $after")
                        null
                    }
        }
    }

}
