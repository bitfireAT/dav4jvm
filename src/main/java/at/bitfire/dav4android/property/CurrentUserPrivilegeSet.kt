/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.dav4android.property

import at.bitfire.dav4android.Constants
import at.bitfire.dav4android.Property
import at.bitfire.dav4android.PropertyFactory
import at.bitfire.dav4android.XmlUtils
import org.xmlpull.v1.XmlPullParser
import java.util.logging.Level

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
            val privs = CurrentUserPrivilegeSet(false, false)

            fun parsePrivilege() {
                val depth = parser.depth
                // <!ELEMENT privilege ANY>

                var eventType = parser.eventType
                while (!(eventType == XmlPullParser.END_TAG && parser.depth == depth)) {
                    if (eventType == XmlPullParser.START_TAG && parser.depth == depth+1 && parser.namespace == XmlUtils.NS_WEBDAV)
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

            try {
                // <!ELEMENT current-user-privilege-set (privilege*)>
                val depth = parser.depth

                var eventType = parser.eventType
                while (!(eventType == XmlPullParser.END_TAG && parser.depth == depth)) {
                    if (eventType == XmlPullParser.START_TAG && parser.depth == depth+1 &&
                            parser.namespace == XmlUtils.NS_WEBDAV && parser.name == "privilege")
                        parsePrivilege()
                    eventType = parser.next()
                }
            } catch(e: Exception) {
                Constants.log.log(Level.SEVERE, "Couldn't parse <current-user-privilege-set>", e)
                return null
            }

            return privs
        }
    }
}
