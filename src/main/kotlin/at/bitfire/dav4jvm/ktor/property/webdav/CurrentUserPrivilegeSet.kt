/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

package at.bitfire.dav4jvm.ktor.property.webdav

import at.bitfire.dav4jvm.ktor.Property
import at.bitfire.dav4jvm.ktor.PropertyFactory
import at.bitfire.dav4jvm.ktor.XmlReader
import at.bitfire.dav4jvm.ktor.XmlUtils.propertyName
import org.xmlpull.v1.XmlPullParser

data class CurrentUserPrivilegeSet(
    // not all privileges from RFC 3744 are implemented by now
    // feel free to add more if you need them for your project
    val mayRead: Boolean = false,
    val mayWriteProperties: Boolean = false,
    val mayWriteContent: Boolean = false,
    val mayBind: Boolean = false,
    val mayUnbind: Boolean = false
): Property {

    companion object {

        @JvmField
        val NAME = Property.Name(NS_WEBDAV, "current-user-privilege-set")

        val PRIVILEGE = Property.Name(NS_WEBDAV, "privilege")
        val READ = Property.Name(NS_WEBDAV, "read")
        val WRITE = Property.Name(NS_WEBDAV, "write")
        val WRITE_PROPERTIES = Property.Name(NS_WEBDAV, "write-properties")
        val WRITE_CONTENT = Property.Name(NS_WEBDAV, "write-content")
        val BIND = Property.Name(NS_WEBDAV, "bind")
        val UNBIND = Property.Name(NS_WEBDAV, "unbind")
        val ALL = Property.Name(NS_WEBDAV, "all")

    }


    object Factory: PropertyFactory {

        override fun getName() = NAME

        override fun create(parser: XmlPullParser): CurrentUserPrivilegeSet {
            // <!ELEMENT current-user-privilege-set (privilege*)>
            // <!ELEMENT privilege ANY>
            var privs = CurrentUserPrivilegeSet()

            XmlReader(parser).processTag(PRIVILEGE) {
                val depth = parser.depth
                var eventType = parser.eventType
                while (!(eventType == XmlPullParser.END_TAG && parser.depth == depth)) {
                    if (eventType == XmlPullParser.START_TAG && parser.depth == depth + 1)
                        when (parser.propertyName()) {
                            READ ->
                                privs = privs.copy(mayRead = true)
                            WRITE -> {
                                privs = privs.copy(
                                    mayBind = true,
                                    mayUnbind = true,
                                    mayWriteProperties = true,
                                    mayWriteContent = true
                                )
                            }
                            WRITE_PROPERTIES ->
                                privs = privs.copy(mayWriteProperties = true)
                            WRITE_CONTENT ->
                                privs = privs.copy(mayWriteContent = true)
                            BIND ->
                                privs = privs.copy(mayBind = true)
                            UNBIND ->
                                privs = privs.copy(mayUnbind = true)
                            ALL -> {
                                privs = privs.copy(
                                    mayRead = true,
                                    mayBind = true,
                                    mayUnbind = true,
                                    mayWriteProperties = true,
                                    mayWriteContent = true
                                )
                            }
                        }
                    eventType = parser.next()
                }
            }

            return privs
        }

    }

}
