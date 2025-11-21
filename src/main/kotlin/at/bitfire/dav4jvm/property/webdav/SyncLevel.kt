/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

package at.bitfire.dav4jvm.property.webdav

import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.PropertyFactory
import at.bitfire.dav4jvm.XmlReader
import org.xmlpull.v1.XmlPullParser

/**
 * Represents a [NS_WEBDAV]`:sync-level` property.
 */
data class SyncLevel(
    /** May be `0`, `1` or [Int.MAX_VALUE] (infinite). */
    val level: Int? = null
): Property {

    object Factory: PropertyFactory {

        override fun getName() = WebDAV.SyncLevel

        override fun create(parser: XmlPullParser): SyncLevel {
            val text = XmlReader(parser).readText()
            val level = if (text == "infinite") Int.MAX_VALUE else text?.toIntOrNull()
            return SyncLevel(level)
        }

    }

}
