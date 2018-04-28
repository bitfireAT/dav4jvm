/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.dav4android.property

import at.bitfire.dav4android.Property
import at.bitfire.dav4android.PropertyFactory
import at.bitfire.dav4android.XmlUtils
import org.xmlpull.v1.XmlPullParser

data class SupportedCalendarComponentSet(
        var supportsEvents: Boolean,
        var supportsTasks: Boolean
): Property {

    companion object {
        @JvmField
        val NAME = Property.Name(XmlUtils.NS_CALDAV, "supported-calendar-component-set")
    }


    class Factory: PropertyFactory {

        override fun getName() = NAME

        override fun create(parser: XmlPullParser): SupportedCalendarComponentSet? {
            /* <!ELEMENT supported-calendar-component-set (comp+)>
               <!ELEMENT comp ((allprop | prop*), (allcomp | comp*))>
               <!ATTLIST comp name CDATA #REQUIRED>
            */
            val components = SupportedCalendarComponentSet(false, false)

            val depth = parser.depth
            var eventType = parser.eventType
            while (!(eventType == XmlPullParser.END_TAG && parser.depth == depth)) {
                if (eventType == XmlPullParser.START_TAG && parser.depth == depth + 1 && parser.namespace == XmlUtils.NS_CALDAV) {
                    when (parser.name) {
                        "allcomp" -> {
                            components.supportsEvents = true
                            components.supportsTasks = true
                        }
                        "comp" ->
                            when (parser.getAttributeValue(null, "name")?.toUpperCase()) {
                                "VEVENT" -> components.supportsEvents = true
                                "VTODO" -> components.supportsTasks = true
                            }
                    }
                }
                eventType = parser.next()
            }

            return components
        }
    }
}
