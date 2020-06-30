/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm.property

import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.PropertyFactory
import at.bitfire.dav4jvm.XmlUtils
import at.bitfire.dav4jvm.XmlUtils.propertyName
import org.xmlpull.v1.XmlPullParser

data class SupportedCalendarComponentSet(
        var supportsEvents: Boolean,
        var supportsTasks: Boolean,
        var supportsJournal: Boolean
): Property {

    companion object {

        @JvmField
        val NAME = Property.Name(XmlUtils.NS_CALDAV, "supported-calendar-component-set")

        val ALLCOMP = Property.Name(XmlUtils.NS_CALDAV, "allcomp")
        val COMP = Property.Name(XmlUtils.NS_CALDAV, "comp")

    }


    class Factory: PropertyFactory {

        override fun getName() = NAME

        override fun create(parser: XmlPullParser): SupportedCalendarComponentSet? {
            /* <!ELEMENT supported-calendar-component-set (comp+)>
               <!ELEMENT comp ((allprop | prop*), (allcomp | comp*))>
               <!ATTLIST comp name CDATA #REQUIRED>
            */
            val components = SupportedCalendarComponentSet(false, false, false)

            val depth = parser.depth
            var eventType = parser.eventType
            while (!(eventType == XmlPullParser.END_TAG && parser.depth == depth)) {
                if (eventType == XmlPullParser.START_TAG && parser.depth == depth + 1) {
                    when (parser.propertyName()) {
                        ALLCOMP -> {
                            components.supportsEvents = true
                            components.supportsTasks = true
                            components.supportsJournal = true
                        }
                        COMP ->
                            when (parser.getAttributeValue(null, "name")?.toUpperCase()) {
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
