/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm.property

import at.bitfire.dav4jvm.Constants
import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.PropertyFactory
import at.bitfire.dav4jvm.XmlUtils
import okhttp3.MediaType
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
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
                XmlUtils.processTag(parser, XmlUtils.NS_CARDDAV, "address-data-type") {
                    parser.getAttributeValue(null, "content-type")?.let { contentType ->
                        var type = contentType
                        parser.getAttributeValue(null, "version")?.let { version -> type += "; version=$version" }
                        MediaType.parse(type)?.let { supported.types.add(it) }
                    }
                }
            } catch(e: XmlPullParserException) {
                Constants.log.log(Level.SEVERE, "Couldn't parse <resourcetype>", e)
                return null
            }

            return supported
        }

    }

}
