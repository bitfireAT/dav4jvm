/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4android.property

import at.bitfire.dav4android.Property
import at.bitfire.dav4android.PropertyFactory
import at.bitfire.dav4android.XmlUtils
import org.xmlpull.v1.XmlPullParser

data class CurrentUserPrivilegeSet(
        // only those privileges which are required for DAVdroid are implemented
        var mayRead: Boolean,
        var mayWriteContent: Boolean
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
            val privs = CurrentUserPrivilegeSet(false, false)

            XmlUtils.processTag(parser, XmlUtils.NS_WEBDAV, "privilege") {
                val depth = parser.depth
                var eventType = parser.eventType
                while (!(eventType == XmlPullParser.END_TAG && parser.depth == depth)) {
                    if (eventType == XmlPullParser.START_TAG && parser.depth == depth + 1 && parser.namespace == XmlUtils.NS_WEBDAV)
                        when (parser.name) {
                            "read" ->
                                privs.mayRead = true
                            "write", "write-content" ->
                                privs.mayWriteContent = true
                            "all" -> {
                                privs.mayRead = true
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
