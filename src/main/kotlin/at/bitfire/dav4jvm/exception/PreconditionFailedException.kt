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

class PreconditionFailedException: HttpException {

    constructor(response: HttpResponse): super(response)
    constructor(message: String?): super(HttpStatusCode.PreconditionFailed, message)

}
