/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm.property.carddav

import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.PropertyFactory
import at.bitfire.dav4jvm.XmlReader
import okhttp3.MediaType
import org.xmlpull.v1.XmlPullParser

class SupportedAddressData: Property {

    companion object {

        @JvmField
        val NAME = Property.Name(NS_CARDDAV, "supported-address-data")

        val ADDRESS_DATA_TYPE = Property.Name(NS_CARDDAV, "address-data-type")
        const val CONTENT_TYPE = "content-type"
        const val VERSION = "version"

    }

    val types = mutableSetOf<MediaType>()

    fun hasVCard4() = types.any { "text/vcard; version=4.0".equals(it.toString(), true) }
    fun hasJCard() = types.any { "application".equals(it.type, true) && "vcard+json".equals(it.subtype, true) }

    override fun toString() = "[${types.joinToString(", ")}]"


    object Factory: PropertyFactory {

        override fun getName() = NAME

        override fun create(parser: XmlPullParser): SupportedAddressData {
            val supported = SupportedAddressData()

            XmlReader(parser).readContentTypes(ADDRESS_DATA_TYPE, supported.types::add)

            return supported
        }

    }

}
