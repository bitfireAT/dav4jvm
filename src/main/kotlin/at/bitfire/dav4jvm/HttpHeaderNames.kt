/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

package at.bitfire.dav4jvm

object HttpHeaderNames {
    /** [RFC4229 Section-2.1.2](https://datatracker.ietf.org/doc/html/rfc4229#section-2.1.2) */
    const val Accept: String = "Accept"

    /** [RFC4229 Section-2.1.5](https://datatracker.ietf.org/doc/html/rfc4229#section-2.1.5) */
    const val AcceptEncoding: String = "Accept-Encoding"

    /** [RFC4229 Section-2.1.45](https://datatracker.ietf.org/doc/html/rfc4229#section-2.1.45) */
    const val ETag: String = "ETag"

    /** [RFC6638 Section-8.2](https://datatracker.ietf.org/doc/html/rfc6638#section-8.2) */
    const val ScheduleTag: String = "Schedule-Tag"
}
