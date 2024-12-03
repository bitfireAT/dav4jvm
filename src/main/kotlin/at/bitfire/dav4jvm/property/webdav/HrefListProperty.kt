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
import java.util.LinkedList
import org.xmlpull.v1.XmlPullParser

/**
 * Represents a list of hrefs.
 *
 * Every [HrefListProperty] must be a data class.
 */
abstract class HrefListProperty(
    open val hrefs: LinkedList<String> = LinkedList<String>()
): Property {

    val href get() = hrefs.firstOrNull()


    abstract class Factory : PropertyFactory {

        fun create(parser: XmlPullParser, list: HrefListProperty): HrefListProperty {
            XmlReader(parser).readTextPropertyList(DavResource.HREF, list.hrefs)
            return list
        }

    }

}
