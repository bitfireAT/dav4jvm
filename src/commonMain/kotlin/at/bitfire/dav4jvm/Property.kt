/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm

import at.bitfire.dav4jvm.Dav4jvm.log
import at.bitfire.dav4jvm.exception.InvalidPropertyException
import nl.adaptivity.xmlutil.EventType
import nl.adaptivity.xmlutil.XmlException
import nl.adaptivity.xmlutil.XmlReader

/**
 * Represents a WebDAV property.
 *
 * Every [Property] must define a static field (use `@JvmStatic`) called `NAME` of type [QName],
 * which will be accessed by reflection.
 */
interface Property {

    companion object {

        fun parse(parser: XmlReader): List<Property> {
            // <!ELEMENT prop ANY >
            val depth = parser.depth
            val properties = mutableListOf<Property>()

            var eventType = parser.eventType
            while (!(eventType == EventType.END_ELEMENT && parser.depth == depth)) {
                if (eventType == EventType.START_ELEMENT && parser.depth == depth + 1) {
                    val depthBeforeParsing = parser.depth
                    val name = parser.name

                    try {
                        val property = PropertyRegistry.create(name, parser)
                        if (parser.depth != depthBeforeParsing) throw XmlException("Invalid depth!")

                        if (property != null) {
                            properties.add(property)
                        } else
                            log.trace("Ignoring unknown property $name")
                    } catch (e: InvalidPropertyException) {
                        log.warn("Ignoring invalid property", e)
                    }
                }
                eventType = parser.next()
            }

            return properties
        }

    }

}
