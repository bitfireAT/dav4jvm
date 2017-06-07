/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.dav4android.property

import android.text.TextUtils
import at.bitfire.dav4android.Property
import at.bitfire.dav4android.PropertyFactory
import at.bitfire.dav4android.XmlUtils
import org.xmlpull.v1.XmlPullParser
import java.util.*

abstract class HrefListProperty: Property {

    val hrefs = LinkedList<String>()

    override fun toString() =  "hrefs=[" + TextUtils.join(", ", hrefs) + "]"


    abstract class Factory: PropertyFactory {

        fun create(parser: XmlPullParser, list: HrefListProperty): HrefListProperty {
            XmlUtils.readTextPropertyList(parser, Property.Name(XmlUtils.NS_WEBDAV, "href"), list.hrefs)
            return list
        }

    }

}