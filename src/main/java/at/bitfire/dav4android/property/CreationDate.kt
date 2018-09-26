/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.dav4android.property

import at.bitfire.dav4android.Property
import at.bitfire.dav4android.PropertyFactory
import at.bitfire.dav4android.XmlUtils
import org.xmlpull.v1.XmlPullParser

data class CreationDate(
        var creationDate: String
): Property {
    companion object {
        @JvmField
        val NAME = Property.Name(XmlUtils.NS_WEBDAV, "creationdate")
    }

    class Factory: PropertyFactory {
        override fun getName() = NAME

        override fun create(parser: XmlPullParser): CreationDate? {
            XmlUtils.readText(parser)?.let { it ->
                return CreationDate(it)
            }
            return null
        }
    }
}