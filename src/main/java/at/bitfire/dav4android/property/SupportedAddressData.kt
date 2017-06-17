/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.dav4android.property

import at.bitfire.dav4android.Constants
import at.bitfire.dav4android.Property
import at.bitfire.dav4android.PropertyFactory
import at.bitfire.dav4android.XmlUtils
import okhttp3.MediaType
import org.xmlpull.v1.XmlPullParser
import java.util.logging.Level

class SupportedAddressData: Property {

    companion object {
        @JvmField
        val NAME = Property.Name(XmlUtils.NS_CARDDAV, "supported-address-data")
    }

    val types = mutableSetOf<MediaType>()

    fun hasVCard4() = types.any { "text/vcard; version=4.0".equals(it.toString(), true) }
    override fun toString() = "[${types.joinToString(", ")}]"


    class Factory: PropertyFactory {

        override fun getName() = NAME

        override fun create(parser: XmlPullParser): SupportedAddressData? {
            val supported = SupportedAddressData()

            try {
                val depth = parser.depth

                var eventType = parser.eventType
                while (!(eventType == XmlPullParser.END_TAG && parser.depth == depth)) {
                    if (eventType == XmlPullParser.START_TAG && parser.depth == depth + 1 &&
                            parser.namespace == XmlUtils.NS_CARDDAV && parser.name == "address-data-type")
                        parser.getAttributeValue(null, "content-type")?.let { contentType ->
                            @Suppress("NAME_SHADOWING")
                            var contentType = contentType
                            parser.getAttributeValue(null, "version")?.let { version -> contentType += "; version=$version" }
                            MediaType.parse(contentType)?.let { supported.types.add(it) }
                        }
                    eventType = parser.next()
                }
            } catch(e: Exception) {
                Constants.log.log(Level.SEVERE, "Couldn't parse <resourcetype>", e)
                return null
            }

            return supported
        }

    }

}
