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
import at.bitfire.dav4jvm.XmlReader
import at.bitfire.dav4jvm.XmlUtils.propertyName
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
        val NAME = Property.Name(WebDAV.NAMESPACE, "current-user-privilege-set")

        val PRIVILEGE = Property.Name(WebDAV.NAMESPACE, "privilege")
        val READ = Property.Name(WebDAV.NAMESPACE, "read")
        val WRITE = Property.Name(WebDAV.NAMESPACE, "write")
        val WRITE_PROPERTIES = Property.Name(WebDAV.NAMESPACE, "write-properties")
        val WRITE_CONTENT = Property.Name(WebDAV.NAMESPACE, "write-content")
        val BIND = Property.Name(WebDAV.NAMESPACE, "bind")
        val UNBIND = Property.Name(WebDAV.NAMESPACE, "unbind")
        val ALL = Property.Name(WebDAV.NAMESPACE, "all")

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
