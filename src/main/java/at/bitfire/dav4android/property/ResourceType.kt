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

class ResourceType: Property {

    companion object {
        @JvmField
        val NAME = Property.Name(XmlUtils.NS_WEBDAV, "resourcetype")

        @JvmField val COLLECTION = Property.Name(XmlUtils.NS_WEBDAV, "collection")    // WebDAV
        @JvmField val PRINCIPAL = Property.Name(XmlUtils.NS_WEBDAV, "principal")      // WebDAV ACL
        @JvmField val ADDRESSBOOK = Property.Name(XmlUtils.NS_CARDDAV, "addressbook") // CardDAV
        @JvmField val CALENDAR = Property.Name(XmlUtils.NS_CALDAV, "calendar")        // CalDAV
        @JvmField val SUBSCRIBED = Property.Name(XmlUtils.NS_CALENDARSERVER, "subscribed")
    }

    val types = mutableSetOf<Property.Name>()

    override fun toString() = "[${types.joinToString(", ")}]"


    class Factory: PropertyFactory {

        override fun getName() = NAME

        override fun create(parser: XmlPullParser): ResourceType? {
            val type = ResourceType()

            val depth = parser.depth
            var eventType = parser.eventType
            while (!(eventType == XmlPullParser.END_TAG && parser.depth == depth)) {
                if (eventType == XmlPullParser.START_TAG && parser.depth == depth + 1) {
                    // use static objects to allow types.contains()
                    var typeName = Property.Name(parser.namespace, parser.name)
                    when (typeName) {
                        COLLECTION -> typeName = COLLECTION
                        PRINCIPAL -> typeName = PRINCIPAL
                        ADDRESSBOOK -> typeName = ADDRESSBOOK
                        CALENDAR -> typeName = CALENDAR
                        SUBSCRIBED -> typeName = SUBSCRIBED
                    }
                    type.types.add(typeName)
                }
                eventType = parser.next()
            }

            return type
        }

    }

}
