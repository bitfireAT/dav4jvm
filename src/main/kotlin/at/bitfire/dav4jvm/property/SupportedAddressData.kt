/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm.property

import at.bitfire.dav4jvm.Dav4jvm
import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.PropertyFactory
import at.bitfire.dav4jvm.XmlUtils
import nl.adaptivity.xmlutil.QName
import nl.adaptivity.xmlutil.XmlException
import nl.adaptivity.xmlutil.XmlReader
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import java.util.logging.Level

class SupportedAddressData : Property {

    companion object {

        @JvmField
        val NAME = QName(XmlUtils.NS_CARDDAV, "supported-address-data")

        val ADDRESS_DATA_TYPE = QName(XmlUtils.NS_CARDDAV, "address-data-type")
        const val CONTENT_TYPE = "content-type"
        const val VERSION = "version"
    }

    val types = mutableSetOf<MediaType>()

    fun hasVCard4() = types.any { "text/vcard; version=4.0".equals(it.toString(), true) }
    fun hasJCard() = types.any { "application".equals(it.type, true) && "vcard+json".equals(it.subtype, true) }

    override fun toString() = "[${types.joinToString(", ")}]"

    object Factory : PropertyFactory {

        override fun getName() = NAME

        override fun create(parser: XmlReader): SupportedAddressData? {
            val supported = SupportedAddressData()

            try {
                XmlUtils.processTag(parser, ADDRESS_DATA_TYPE) {
                    parser.getAttributeValue(null, CONTENT_TYPE)?.let { contentType ->
                        var type = contentType
                        parser.getAttributeValue(null, VERSION)?.let { version -> type += "; version=$version" }
                        type.toMediaTypeOrNull()?.let { supported.types.add(it) }
                    }
                }
            } catch (e: XmlException) {
                Dav4jvm.log.log(Level.SEVERE, "Couldn't parse <resourcetype>", e)
                return null
            }

            return supported
        }
    }
}
