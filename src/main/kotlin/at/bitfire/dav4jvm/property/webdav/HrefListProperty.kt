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
import java.util.*

abstract class HrefListProperty: Property {

    val hrefs = LinkedList<String>()

    val href
        get() = hrefs.firstOrNull()

    override fun toString() =  "href=[" + hrefs.joinToString(", ") + "]"


    abstract class Factory : PropertyFactory {

        fun create(parser: XmlPullParser, list: HrefListProperty): HrefListProperty {
            XmlReader(parser).readTextPropertyList(DavResource.HREF, list.hrefs)
            return list
        }

    }

}