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
import at.bitfire.dav4jvm.okhttp.XmlUtils.propertyName
import org.xmlpull.v1.XmlPullParser

data class SupportedCalendarComponentSet(
    val supportsEvents: Boolean,
    val supportsTasks: Boolean,
    val supportsJournal: Boolean
): Property {

    companion object {

        @JvmField
        val NAME = Property.Name(NS_CALDAV, "supported-calendar-component-set")

        val ALLCOMP = Property.Name(NS_CALDAV, "allcomp")
        val COMP = Property.Name(NS_CALDAV, "comp")

    }


    object Factory: PropertyFactory {

        override fun getName() = NAME

        override fun create(parser: XmlPullParser): SupportedCalendarComponentSet {
            /* <!ELEMENT supported-calendar-component-set (comp+)>
               <!ELEMENT comp ((allprop | prop*), (allcomp | comp*))>
               <!ATTLIST comp name CDATA #REQUIRED>
            */
            var components = SupportedCalendarComponentSet(
                supportsEvents = false,
                supportsTasks = false,
                supportsJournal = false
            )

            val depth = parser.depth
            var eventType = parser.eventType
            while (!(eventType == XmlPullParser.END_TAG && parser.depth == depth)) {
                if (eventType == XmlPullParser.START_TAG && parser.depth == depth + 1) {
                    when (parser.propertyName()) {
                        ALLCOMP -> {
                            components = SupportedCalendarComponentSet(
                                supportsEvents = true,
                                supportsTasks = true,
                                supportsJournal = true
                            )
                        }
                        COMP ->
                            when (parser.getAttributeValue(null, "name")?.uppercase()) {
                                "VEVENT" -> components = components.copy(supportsEvents = true)
                                "VTODO" -> components = components.copy(supportsTasks = true)
                                "VJOURNAL" -> components = components.copy(supportsJournal = true)
                            }
                    }
                }
                eventType = parser.next()
            }

            return components
        }
    }
}
