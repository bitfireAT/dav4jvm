/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm.property.carddav

import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.PropertyFactory
import at.bitfire.dav4jvm.XmlReader
import org.xmlpull.v1.XmlPullParser

data class AddressData(
        val card: String?
): Property {

    companion object {
        @JvmField
        val NAME = Property.Name(NS_CARDDAV, "address-data")

        // attributes
        const val CONTENT_TYPE = "content-type"
        const val VERSION = "version"
    }


    object Factory: PropertyFactory {

        override fun getName() = NAME

        override fun create(parser: XmlPullParser) =
            // <!ELEMENT address-data (#PCDATA)>
            AddressData(XmlReader(parser).readText())

    }

}
