/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm.exception

/**
 * Represents an invalid XML (WebDAV) property. This is for instance thrown
 * when parsing something like `<multistatus>...<getetag><novalue/></getetag>`
 * because a text value would be expected.
 */
class InvalidPropertyException(message: String): Exception(message)
