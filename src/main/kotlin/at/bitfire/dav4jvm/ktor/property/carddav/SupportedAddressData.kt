/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

package at.bitfire.dav4jvm.ktor.property.carddav

import at.bitfire.dav4jvm.ktor.Property
import at.bitfire.dav4jvm.ktor.PropertyFactory
import at.bitfire.dav4jvm.ktor.XmlReader
import io.ktor.http.ContentType
import org.xmlpull.v1.XmlPullParser

class SupportedAddressData(
    val types: Set<ContentType> = emptySet()
): Property {

    companion object {

        @JvmField
        val NAME = Property.Name(NS_CARDDAV, "supported-address-data")

        val ADDRESS_DATA_TYPE = Property.Name(NS_CARDDAV, "address-data-type")
        const val CONTENT_TYPE = "content-type"
        const val VERSION = "version"

    }

    fun hasVCard4() = types.any { "text/vcard; version=4.0".equals(it.toString(), true) }
    fun hasJCard() = types.any { ContentType.Application.contains(it) && "vcard+json".equals(it.contentSubtype, true) }

    override fun toString() = "[${types.joinToString(", ")}]"


    object Factory: PropertyFactory {

        override fun getName() = NAME

        override fun create(parser: XmlPullParser): SupportedAddressData {
            val supportedTypes = mutableSetOf<ContentType>()

            XmlReader(parser).readContentTypes(ADDRESS_DATA_TYPE, supportedTypes::add)

            return SupportedAddressData(supportedTypes)
        }

    }

}
