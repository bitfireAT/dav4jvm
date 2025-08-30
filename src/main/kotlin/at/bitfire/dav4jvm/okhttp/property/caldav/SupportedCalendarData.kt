/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

package at.bitfire.dav4jvm.okhttp.property.caldav

import at.bitfire.dav4jvm.okhttp.Property
import at.bitfire.dav4jvm.okhttp.PropertyFactory
import at.bitfire.dav4jvm.okhttp.XmlReader
import okhttp3.MediaType
import org.xmlpull.v1.XmlPullParser

data class SupportedCalendarData(
    val types: Set<MediaType> = emptySet()
): Property {

    companion object {

        @JvmField
        val NAME = Property.Name(NS_CALDAV, "supported-calendar-data")

        val CALENDAR_DATA_TYPE = Property.Name(NS_CALDAV, "calendar-data")
        const val CONTENT_TYPE = "content-type"
        const val VERSION = "version"

    }

    fun hasJCal() = types.any { "application".equals(it.type, true) && "calendar+json".equals(it.subtype, true) }


    object Factory: PropertyFactory {

        override fun getName() = NAME

        override fun create(parser: XmlPullParser): SupportedCalendarData {
            val supportedTypes = mutableSetOf<MediaType>()

            XmlReader(parser).readContentTypes(CALENDAR_DATA_TYPE, supportedTypes::add)

            return SupportedCalendarData(supportedTypes)
        }

    }

}
