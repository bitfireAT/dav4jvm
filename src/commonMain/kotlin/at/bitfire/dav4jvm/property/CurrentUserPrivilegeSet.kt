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

data class CurrentUserPrivilegeSet(
    // not all privileges from RFC 3744 are implemented by now
    // feel free to add more if you need them for your project
    var mayRead: Boolean = false,
    var mayWriteProperties: Boolean = false,
    var mayWriteContent: Boolean = false,
    var mayBind: Boolean = false,
    var mayUnbind: Boolean = false
) : Property {

    companion object {

        @JvmField
        val NAME = QName(XmlUtils.NS_WEBDAV, "current-user-privilege-set")

        val PRIVILEGE = QName(XmlUtils.NS_WEBDAV, "privilege")
        val READ = QName(XmlUtils.NS_WEBDAV, "read")
        val WRITE = QName(XmlUtils.NS_WEBDAV, "write")
        val WRITE_PROPERTIES = QName(XmlUtils.NS_WEBDAV, "write-properties")
        val WRITE_CONTENT = QName(XmlUtils.NS_WEBDAV, "write-content")
        val BIND = QName(XmlUtils.NS_WEBDAV, "bind")
        val UNBIND = QName(XmlUtils.NS_WEBDAV, "unbind")
        val ALL = QName(XmlUtils.NS_WEBDAV, "all")

    }


    object Factory : PropertyFactory {

        override fun getName() = NAME

        override fun create(parser: XmlReader): CurrentUserPrivilegeSet {
            // <!ELEMENT current-user-privilege-set (privilege*)>
            // <!ELEMENT privilege ANY>
            val privs = CurrentUserPrivilegeSet()

            XmlUtils.processTag(parser, PRIVILEGE) {
                val depth = parser.depth
                var eventType = parser.eventType
                while (!(eventType == EventType.END_ELEMENT && parser.depth == depth)) {
                    if (eventType == EventType.START_ELEMENT && parser.depth == depth + 1)
                        when (parser.name) {
                            READ ->
                                privs.mayRead = true

                            WRITE -> {
                                privs.mayBind = true
                                privs.mayUnbind = true
                                privs.mayWriteProperties = true
                                privs.mayWriteContent = true
                            }

                            WRITE_PROPERTIES ->
                                privs.mayWriteProperties = true

                            WRITE_CONTENT ->
                                privs.mayWriteContent = true

                            BIND ->
                                privs.mayBind = true

                            UNBIND ->
                                privs.mayUnbind = true

                            ALL -> {
                                privs.mayRead = true
                                privs.mayBind = true
                                privs.mayUnbind = true
                                privs.mayWriteProperties = true
                                privs.mayWriteContent = true
                            }
                        }
                    eventType = parser.next()
                }
            }

            return privs
        }
    }
}
