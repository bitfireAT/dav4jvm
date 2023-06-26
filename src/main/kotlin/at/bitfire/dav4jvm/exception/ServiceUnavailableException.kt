/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm.exception

import at.bitfire.dav4jvm.Dav4jvm
import at.bitfire.dav4jvm.HttpUtils
import okhttp3.Response
import java.net.HttpURLConnection
import java.util.*

class ServiceUnavailableException : HttpException {

    var retryAfter: Date? = null

    constructor(message: String?) : super(HttpURLConnection.HTTP_UNAVAILABLE, message)

    constructor(response: Response) : super(response) {
        // Retry-After  = "Retry-After" ":" ( HTTP-date | delta-seconds )
        // HTTP-date    = rfc1123-date | rfc850-date | asctime-date

        response.header("Retry-After")?.let { after ->
            retryAfter = HttpUtils.parseDate(after)
                ?: // not a HTTP-date, must be delta-seconds
                try {
                    val seconds = Integer.parseInt(after)

                    val cal = Calendar.getInstance()
                    cal.add(Calendar.SECOND, seconds)
                    cal.time
                } catch (ignored: NumberFormatException) {
                    Dav4jvm.log.warning("Received Retry-After which was not a HTTP-date nor delta-seconds: $after")
                    null
                }
        }
    }
}
