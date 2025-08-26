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

import okhttp3.Response

/**
 * Signals that a HTTP error was sent by the server in the context of a WebDAV operation.
 */
open class HttpException: DavException {

    constructor(response: Response) : super(
        message = "HTTP ${response.code} ${response.message}",
        response = response
    )

}