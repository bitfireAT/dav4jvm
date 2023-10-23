/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm.property

import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.PropertyFactory
import at.bitfire.dav4jvm.XmlUtils
import org.xmlpull.v1.XmlPullParser

class ResourceType: Property {

    companion object {
        @JvmField
        val NAME = Property.Name(XmlUtils.NS_WEBDAV, "resourcetype")

        val COLLECTION = Property.Name(XmlUtils.NS_WEBDAV, "collection")    // WebDAV
        val PRINCIPAL = Property.Name(XmlUtils.NS_WEBDAV, "principal")      // WebDAV ACL
        val ADDRESSBOOK = Property.Name(XmlUtils.NS_CARDDAV, "addressbook") // CardDAV
        val CALENDAR = Property.Name(XmlUtils.NS_CALDAV, "calendar")        // CalDAV

        // CalendarServer extensions
        val CALENDAR_PROXY_READ = Property.Name(XmlUtils.NS_CALENDARSERVER, "calendar-proxy-read")      // CalDAV Proxy
        val CALENDAR_PROXY_WRITE = Property.Name(XmlUtils.NS_CALENDARSERVER, "calendar-proxy-write")    // CalDAV Proxy
        val SUBSCRIBED = Property.Name(XmlUtils.NS_CALENDARSERVER, "subscribed")
    }

    val types = mutableSetOf<Property.Name>()

    override fun toString() = "[${types.joinToString(", ")}]"


    object Factory: PropertyFactory {

        override fun getName() = NAME

        override fun create(parser: XmlPullParser): ResourceType? {
            val type = ResourceType()

            val depth = parser.depth
            var eventType = parser.eventType
            while (!(eventType == XmlPullParser.END_TAG && parser.depth == depth)) {
                if (eventType == XmlPullParser.START_TAG && parser.depth == depth + 1) {
                    // use static objects to allow types.contains()
                    var typeName = Property.Name(parser.namespace, parser.name)
                    when (typeName) {       // if equals(), replace by our instance
                        COLLECTION -> typeName = COLLECTION
                        PRINCIPAL -> typeName = PRINCIPAL
                        ADDRESSBOOK -> typeName = ADDRESSBOOK
                        CALENDAR -> typeName = CALENDAR
                        CALENDAR_PROXY_READ -> typeName = CALENDAR_PROXY_READ
                        CALENDAR_PROXY_WRITE -> typeName = CALENDAR_PROXY_WRITE
                        SUBSCRIBED -> typeName = SUBSCRIBED
                    }
                    type.types.add(typeName)
                }
                eventType = parser.next()
            }
            assert(parser.depth == depth)

            return type
        }

    }

}
