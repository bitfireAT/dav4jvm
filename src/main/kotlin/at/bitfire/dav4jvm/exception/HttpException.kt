/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

package at.bitfire.dav4jvm.exception

import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode

/**
 * Signals that a HTTP error was sent by the server.
 */
open class HttpException: DavException {

    var code: HttpStatusCode

    constructor(response: HttpResponse): super(
            "HTTP ${response.status.value} ${response.status.description}",    // TODO: originally message instead of description, is this the same?
            httpResponse = response
    ) {
        code = response.status
    }

    constructor(code: HttpStatusCode, message: String?): super("HTTP $code $message") {
        this.code = code
    }

}
