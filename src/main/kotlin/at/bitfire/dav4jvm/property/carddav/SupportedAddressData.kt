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
import io.ktor.http.ContentType
import org.xmlpull.v1.XmlPullParser

class SupportedAddressData(
    val types: Set<String> = emptySet()
): Property {

    companion object {

        const val CONTENT_TYPE = "content-type"
        const val VERSION = "version"

    }

    fun hasVCard4() = types
        .map { try { ContentType.parse(it) } catch (_: Exception) { ContentType.Any } }
        .any { "text/vcard; version=4.0".equals(it.toString(), true) }
    fun hasJCard() = types
        .map { try { ContentType.parse(it) } catch (_: Exception) { ContentType.Any } }
        .any { ContentType.Application.contains(it) && "vcard+json".equals(it.contentSubtype, true) }

    override fun toString() = "[${types.joinToString(", ")}]"


    object Factory: PropertyFactory {

        override fun getName() = CardDAV.SupportedAddressData

        override fun create(parser: XmlPullParser): SupportedAddressData {
            val supportedTypes = mutableSetOf<String>()

            XmlReader(parser).readContentTypes(CardDAV.AddressDataType, supportedTypes::add)

            return SupportedAddressData(supportedTypes)
        }

    }

}
