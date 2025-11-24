/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

package at.bitfire.dav4jvm.ktor

import at.bitfire.dav4jvm.Error
import at.bitfire.dav4jvm.Property
import io.ktor.http.HttpStatusCode

/**
 * Represents a WebDAV propstat XML element.
 *
 *     <!ELEMENT propstat (prop, status, error?, responsedescription?) >
 */
data class PropStat(
    val properties: List<Property>,
    val status: HttpStatusCode,
    val error: List<Error>? = null
)