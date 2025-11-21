/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

package at.bitfire.dav4jvm.property.common

import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.PropertyFactory
import at.bitfire.dav4jvm.XmlReader
import at.bitfire.dav4jvm.property.webdav.WebDAV
import org.xmlpull.v1.XmlPullParser

/**
 * Represents a list of hrefs.
 *
 * Every [HrefListProperty] must be a data class.
 */
abstract class HrefListProperty(
    open val hrefs: List<String>
): Property {

    abstract class Factory : PropertyFactory {

        @Deprecated("hrefs is no longer mutable.", level = DeprecationLevel.ERROR)
        fun create(parser: XmlPullParser, list: HrefListProperty): HrefListProperty {
            val hrefs = list.hrefs.toMutableList()
            XmlReader(parser).readTextPropertyList(WebDAV.Href, hrefs)
            return list
        }

        fun <PropertyType> create(
            parser: XmlPullParser,
            constructor: (hrefs: List<String>
                ) -> PropertyType): PropertyType {
            val hrefs = mutableListOf<String>()
            XmlReader(parser).readTextPropertyList(WebDAV.Href, hrefs)
            return constructor(hrefs)
        }

    }

}