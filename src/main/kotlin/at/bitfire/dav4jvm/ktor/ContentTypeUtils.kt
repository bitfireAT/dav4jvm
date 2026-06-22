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

import io.ktor.http.BadContentTypeFormatException
import io.ktor.http.ContentType

/**
 * Checks if this content type represents a text format.
 *
 * @return true if this content type is XML or matches Text.Any
 */
fun ContentType.isText() =
    isXml() || match(ContentType.Text.Any)

/**
 * Checks if this content type represents an XML format.
 *
 * @return true if this content type matches Application.Xml or Text.Xml
 */
fun ContentType.isXml() =
    match(ContentType.Application.Xml) || match(ContentType.Text.Xml)

/**
 * Converts a string to a [ContentType], if possible.
 *
 * @return the [ContentType], or `null` if the string is null or couldn't be parsed
 */
fun String?.toContentTypeOrNull(): ContentType? {
    if (this == null)
        return null

    return try {
        ContentType.parse(this)
    } catch (_: BadContentTypeFormatException) {
        null
    }
}
