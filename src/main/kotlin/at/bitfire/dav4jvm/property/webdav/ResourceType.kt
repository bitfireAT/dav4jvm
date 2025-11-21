/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

package at.bitfire.dav4jvm.property.webdav

import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.PropertyFactory
import at.bitfire.dav4jvm.property.caldav.NS_CALDAV
import at.bitfire.dav4jvm.property.caldav.NS_CALENDARSERVER
import at.bitfire.dav4jvm.property.carddav.NS_CARDDAV
import org.xmlpull.v1.XmlPullParser

class ResourceType(
    val types: Set<Property.Name> = emptySet()
): Property {

    companion object {

        val PRINCIPAL = Property.Name(WebDAV.NS_WEBDAV, "principal")      // WebDAV ACL
        val ADDRESSBOOK = Property.Name(NS_CARDDAV, "addressbook") // CardDAV
        val CALENDAR = Property.Name(NS_CALDAV, "calendar")        // CalDAV

        // CalendarServer extensions
        val CALENDAR_PROXY_READ = Property.Name(NS_CALENDARSERVER, "calendar-proxy-read")      // CalDAV Proxy
        val CALENDAR_PROXY_WRITE = Property.Name(NS_CALENDARSERVER, "calendar-proxy-write")    // CalDAV Proxy
        val SUBSCRIBED = Property.Name(NS_CALENDARSERVER, "subscribed")

    }


    object Factory: PropertyFactory {

        override fun getName() = WebDAV.ResourceType

        override fun create(parser: XmlPullParser): ResourceType {
            val types = mutableSetOf<Property.Name>()

            val depth = parser.depth
            var eventType = parser.eventType
            while (!(eventType == XmlPullParser.END_TAG && parser.depth == depth)) {
                if (eventType == XmlPullParser.START_TAG && parser.depth == depth + 1) {
                    // use static objects to allow types.contains()
                    var typeName = Property.Name(parser.namespace, parser.name)
                    when (typeName) {       // if equals(), replace by our instance
                        WebDAV.Collection -> typeName = WebDAV.Collection
                        PRINCIPAL -> typeName = PRINCIPAL
                        ADDRESSBOOK -> typeName = ADDRESSBOOK
                        CALENDAR -> typeName = CALENDAR
                        CALENDAR_PROXY_READ -> typeName = CALENDAR_PROXY_READ
                        CALENDAR_PROXY_WRITE -> typeName = CALENDAR_PROXY_WRITE
                        SUBSCRIBED -> typeName = SUBSCRIBED
                    }
                    types.add(typeName)
                }
                eventType = parser.next()
            }
            assert(parser.depth == depth)

            return ResourceType(types)
        }

    }

}
