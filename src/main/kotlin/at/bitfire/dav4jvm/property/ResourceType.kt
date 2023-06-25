/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm.property

import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.PropertyFactory
import at.bitfire.dav4jvm.XmlUtils
import nl.adaptivity.xmlutil.QName
import nl.adaptivity.xmlutil.XmlReader
import kotlin.jvm.JvmField

class ResourceType : Property {

    companion object {
        @JvmField
        val NAME = QName(XmlUtils.NS_WEBDAV, "resourcetype")

        val COLLECTION = QName(XmlUtils.NS_WEBDAV, "collection") // WebDAV
        val PRINCIPAL = QName(XmlUtils.NS_WEBDAV, "principal") // WebDAV ACL
        val ADDRESSBOOK = QName(XmlUtils.NS_CARDDAV, "addressbook") // CardDAV
        val CALENDAR = QName(XmlUtils.NS_CALDAV, "calendar") // CalDAV
        val SUBSCRIBED = QName(XmlUtils.NS_CALENDARSERVER, "subscribed")
    }

    val types = mutableSetOf<QName>()

    override fun toString() = "[${types.joinToString(", ")}]"

    object Factory : PropertyFactory {

        override fun getName() = NAME

        override fun create(parser: XmlReader): ResourceType {
            val type = ResourceType()

            XmlUtils.processTag(parser) {
                // use static objects to allow types.contains()
                var typeName = parser.name
                when (typeName) {
                    COLLECTION -> typeName = COLLECTION
                    PRINCIPAL -> typeName = PRINCIPAL
                    ADDRESSBOOK -> typeName = ADDRESSBOOK
                    CALENDAR -> typeName = CALENDAR
                    SUBSCRIBED -> typeName = SUBSCRIBED
                }
                type.types.add(typeName)
            }

            return type
        }
    }
}
