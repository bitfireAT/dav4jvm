/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

package at.bitfire.dav4jvm

import at.bitfire.dav4jvm.exception.InvalidPropertyException
import org.xmlpull.v1.XmlPullParser
import java.io.Serializable
import java.util.LinkedList
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Represents a WebDAV property.
 *
 * Every [Property] must define a static field (use `@JvmStatic`) called `NAME` of type [Property.Name],
 * which will be accessed by reflection.
 *
 * Every [Property] should be a data class in order to be able to compare it against others, and convert to a useful
 * string for debugging.
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
            val logger = Logger.getLogger(Property::javaClass.name)

            // <!ELEMENT prop ANY >
            val depth = parser.depth
            val properties = LinkedList<Property>()

            var eventType = parser.eventType
            while (!(eventType == XmlPullParser.END_TAG && parser.depth == depth)) {
                if (eventType == XmlPullParser.START_TAG && parser.depth == depth + 1) {
                    val depthBeforeParsing = parser.depth
                    val name = Name(parser.namespace, parser.name)

                    try {
                        val property = PropertyRegistry.create(name, parser)
                        assert(parser.depth == depthBeforeParsing)

                        if (property != null) {
                            properties.add(property)
                        } else
                            logger.fine("Ignoring unknown property $name")
                    } catch (e: InvalidPropertyException) {
                        logger.log(Level.WARNING, "Ignoring invalid property", e)
                    }
                }

                eventType = parser.next()
            }

            return properties
        }

    }

}
