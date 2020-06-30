/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm

import at.bitfire.dav4jvm.Dav4jvm.log
import org.xmlpull.v1.XmlPullParser
import java.io.Serializable
import java.util.*

/**
 * Represents a WebDAV property.
 *
 * Every [Property] must define a static field (use `@JvmStatic`) called `NAME` of type [Property.Name],
 * which will be accessed by reflection.
 */
interface Property {

    data class Name(
            val namespace: String,
            val name: String
    ): Serializable {

        override fun toString() = "$namespace:$name"

    }

    companion object {

        fun parse(parser: XmlPullParser): List<Property> {
            // <!ELEMENT prop ANY >
            val depth = parser.depth
            val properties = LinkedList<Property>()

            var eventType = parser.eventType
            while (!(eventType == XmlPullParser.END_TAG && parser.depth == depth)) {
                if (eventType == XmlPullParser.START_TAG && parser.depth == depth + 1) {
                    val depthBeforeParsing = parser.depth
                    val name = Property.Name(parser.namespace, parser.name)
                    val property = PropertyRegistry.create(name, parser)
                    assert(parser.depth == depthBeforeParsing)

                    if (property != null) {
                        properties.add(property)
                    } else
                        log.fine("Ignoring unknown property $name")
                }
                eventType = parser.next()
            }

            return properties
        }

    }

}
