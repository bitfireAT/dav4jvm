/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.dav4android.exception

import at.bitfire.dav4android.Constants
import okhttp3.Response
import okhttp3.internal.http.HttpDate
import java.net.HttpURLConnection
import java.util.*

class ServiceUnavailableException: HttpException {

    var retryAfter: Date? = null

    constructor(message: String?): super(HttpURLConnection.HTTP_UNAVAILABLE, message) {
        retryAfter = null
    }

    constructor(response: Response): super(response) {
        // Retry-After  = "Retry-After" ":" ( HTTP-date | delta-seconds )
        // HTTP-date    = rfc1123-date | rfc850-date | asctime-date

        response.header("Retry-After")?.let {
            retryAfter = HttpDate.parse(it) ?:
                    // not a HTTP-date, must be delta-seconds
                    try {
                        val seconds = Integer.parseInt(it)

                        val cal = Calendar.getInstance()
                        cal.add(Calendar.SECOND, seconds)
                        cal.time

                    } catch (ignored: NumberFormatException) {
                        Constants.log.warning("Received Retry-After which was not a HTTP-date nor delta-seconds")
                        null
                    }
        }
    }

}
