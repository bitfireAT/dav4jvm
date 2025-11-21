/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

package at.bitfire.dav4jvm.property.caldav

import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.PropertyFactory
import at.bitfire.dav4jvm.XmlReader
import io.ktor.http.ContentType
import org.xmlpull.v1.XmlPullParser

data class SupportedCalendarData(
    val types: Set<String> = emptySet()
): Property {

    companion object {

        const val CONTENT_TYPE = "content-type"
        const val VERSION = "version"

    }

    fun hasJCal() = types
        .map { ContentType.parse(it) }
        .any { ContentType.Application.contains(it) && "calendar+json".equals(it.contentSubtype, true) }


    object Factory: PropertyFactory {

        override fun getName() = CalDAV.SupportedCalendarData

        override fun create(parser: XmlPullParser): SupportedCalendarData {
            val supportedTypes = mutableSetOf<String>()

            XmlReader(parser).readContentTypes(CalDAV.CalendarData, supportedTypes::add)

            return SupportedCalendarData(supportedTypes)
        }

    }

}
