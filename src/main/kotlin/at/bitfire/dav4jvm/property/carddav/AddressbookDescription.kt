/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

package at.bitfire.dav4jvm.property.carddav

import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.PropertyFactory
import at.bitfire.dav4jvm.XmlReader
import org.xmlpull.v1.XmlPullParser

data class AddressbookDescription(
    val description: String? = null
): Property {

    object Factory: PropertyFactory {

        override fun getName() = CardDAV.AddressbookDescription

        override fun create(parser: XmlPullParser) =
            // <!ELEMENT addressbook-description (#PCDATA)>
            AddressbookDescription(XmlReader(parser).readText())

    }

}
