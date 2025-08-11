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

import okhttp3.Response

interface DavExceptionCompanion<CL: DavException> {
    fun constructor(message: String?): CL

    fun fromHttpResponse(httpResponse: Response): CL {
        return constructor(httpResponse.message).apply { populateHttpResponse(httpResponse) }
    }

    fun fromMessage(message: String?): CL = constructor(message)
}
