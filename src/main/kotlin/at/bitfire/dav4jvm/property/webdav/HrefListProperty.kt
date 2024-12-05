/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm.property.webdav

import at.bitfire.dav4jvm.DavResource
import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.PropertyFactory
import at.bitfire.dav4jvm.XmlReader
import org.xmlpull.v1.XmlPullParser

/**
 * Represents a list of hrefs.
 *
 * Every [HrefListProperty] must be a data class.
 */
abstract class HrefListProperty(
    open val hrefs: List<String>
): Property {

    val href get() = hrefs.firstOrNull()


    abstract class Factory : PropertyFactory {

        @Deprecated("hrefs is no longer mutable.", level = DeprecationLevel.ERROR)
        fun create(parser: XmlPullParser, list: HrefListProperty): HrefListProperty {
            val hrefs = list.hrefs.toMutableList()
            XmlReader(parser).readTextPropertyList(DavResource.HREF, hrefs)
            return list
        }

        fun <PropertyType> create(
            parser: XmlPullParser,
            constructor: (hrefs: List<String>
                ) -> PropertyType): PropertyType {
            val hrefs = mutableListOf<String>()
            XmlReader(parser).readTextPropertyList(DavResource.HREF, hrefs)
            return constructor(hrefs)
        }

    }

}
