/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm.exception

import io.ktor.client.statement.*

/**
 * Signals that a HTTP error was sent by the server.
 */
open class HttpException(response: HttpResponse) : DavException(
    "HTTP ${response.status}",
    httpResponse = response
) {

    var code: Int

    init {
        code = response.status.value
    }

}
