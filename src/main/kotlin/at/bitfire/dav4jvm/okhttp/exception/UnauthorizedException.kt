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

class UnauthorizedException: HttpException {

    constructor(response: Response) : super(response) {
        if (response.code != 401)
            throw IllegalArgumentException("Status code must be 401")
    }

}