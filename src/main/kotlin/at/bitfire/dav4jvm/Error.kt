/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.dav4jvm

import org.xmlpull.v1.XmlPullParser
import java.io.Serializable

/**
 * Represents an XML precondition/postcondition error. Every error has a name, which is the XML element
 * name. Subclassed errors may have more specific information available.
 *
 * At the moment, there is no logic for subclassing errors.
 */
class Error(
        val name: Property.Name
): Serializable {

    companion object {

        fun parseError(parser: XmlPullParser): List<Error> {
            val names = mutableSetOf<Property.Name>()

            val depth = parser.depth
            var eventType = parser.eventType
            while (!(eventType == XmlPullParser.END_TAG && parser.depth == depth)) {
                if (eventType == XmlPullParser.START_TAG && parser.depth == depth + 1)
                    names += Property.Name(parser.namespace, parser.name)
                eventType = parser.next()
            }

            return names.map { Error(it) }
        }


        // some pre-defined errors

        val NEED_PRIVILEGES = Error(Property.Name(XmlUtils.NS_WEBDAV, "need-privileges"))
        val VALID_SYNC_TOKEN = Error(Property.Name(XmlUtils.NS_WEBDAV, "valid-sync-token"))

    }

    override fun equals(other: Any?) =
            (other is Error) && other.name == name

    override fun hashCode() = name.hashCode()

}
