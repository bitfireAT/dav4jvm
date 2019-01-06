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

data class CurrentUserPrivilegeSet(
        // not all privileges from RFC 3744 are implemented by now
        // feel free to add more if you need them for your project
        var mayRead: Boolean = false,
        var mayWriteProperties: Boolean = false,
        var mayWriteContent: Boolean = false,
        var mayBind: Boolean = false,
        var mayUnbind: Boolean = false
): Property {

    companion object {
        @JvmField
        val NAME = Property.Name(XmlUtils.NS_WEBDAV, "current-user-privilege-set")
    }


    class Factory: PropertyFactory {

        override fun getName() = NAME

        override fun create(parser: XmlPullParser): CurrentUserPrivilegeSet? {
            // <!ELEMENT current-user-privilege-set (privilege*)>
            // <!ELEMENT privilege ANY>
            val privs = CurrentUserPrivilegeSet()

            XmlUtils.processTag(parser, XmlUtils.NS_WEBDAV, "privilege") {
                val depth = parser.depth
                var eventType = parser.eventType
                while (!(eventType == XmlPullParser.END_TAG && parser.depth == depth)) {
                    if (eventType == XmlPullParser.START_TAG && parser.depth == depth + 1 && parser.namespace == XmlUtils.NS_WEBDAV)
                        when (parser.name) {
                            "read" ->
                                privs.mayRead = true
                            "write" -> {
                                privs.mayBind = true
                                privs.mayUnbind = true
                                privs.mayWriteProperties = true
                                privs.mayWriteContent = true
                            }
                            "write-properties" ->
                                privs.mayWriteProperties = true
                            "write-content" ->
                                privs.mayWriteContent = true
                            "bind" ->
                                privs.mayBind = true
                            "unbind" ->
                                privs.mayUnbind = true
                            "all" -> {
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
