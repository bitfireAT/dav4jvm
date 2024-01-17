/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm.property.carddav

import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.PropertyFactory
import at.bitfire.dav4jvm.XmlUtils
import org.xmlpull.v1.XmlPullParser

data class AddressbookDescription(
        var description: String? = null
): Property {

    companion object {
        @JvmField
        val NAME = Property.Name(NS_CARDDAV, "addressbook-description")
    }

    object Factory: PropertyFactory {

        override fun getName() = NAME

        override fun create(parser: XmlPullParser) =
                // <!ELEMENT addressbook-description (#PCDATA)>
                AddressbookDescription(XmlUtils.readText(parser))

    }

}
