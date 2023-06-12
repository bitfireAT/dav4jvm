/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm.property

import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.PropertyFactory
import at.bitfire.dav4jvm.XmlUtils
import nl.adaptivity.xmlutil.EventType
import nl.adaptivity.xmlutil.QName
import nl.adaptivity.xmlutil.XmlReader
import kotlin.jvm.JvmField

data class SupportedCalendarComponentSet(
    var supportsEvents: Boolean,
    var supportsTasks: Boolean,
    var supportsJournal: Boolean
) : Property {

    companion object {

        @JvmField
        val NAME = QName(XmlUtils.NS_CALDAV, "supported-calendar-component-set")

        val ALLCOMP = QName(XmlUtils.NS_CALDAV, "allcomp")
        val COMP = QName(XmlUtils.NS_CALDAV, "comp")

    }


    object Factory : PropertyFactory {

        override fun getName() = NAME

        override fun create(parser: XmlReader): SupportedCalendarComponentSet {
            /* <!ELEMENT supported-calendar-component-set (comp+)>
               <!ELEMENT comp ((allprop | prop*), (allcomp | comp*))>
               <!ATTLIST comp name CDATA #REQUIRED>
            */
            val components = SupportedCalendarComponentSet(false, false, false)

            val depth = parser.depth
            var eventType = parser.eventType
            while (!(eventType == EventType.END_ELEMENT && parser.depth == depth)) {
                if (eventType == EventType.START_ELEMENT && parser.depth == depth + 1) {
                    when (parser.name) {
                        ALLCOMP -> {
                            components.supportsEvents = true
                            components.supportsTasks = true
                            components.supportsJournal = true
                        }

                        COMP ->
                            when (parser.getAttributeValue(null, "name")?.uppercase()) {
                                "VEVENT" -> components.supportsEvents = true
                                "VTODO" -> components.supportsTasks = true
                                "VJOURNAL" -> components.supportsJournal = true
                            }
                    }
                }
                eventType = parser.next()
            }

            return components
        }
    }
}
