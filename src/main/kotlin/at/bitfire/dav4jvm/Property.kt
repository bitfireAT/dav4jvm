/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm

import at.bitfire.dav4jvm.Dav4jvm.log
import at.bitfire.dav4jvm.exception.InvalidPropertyException
import nl.adaptivity.xmlutil.XmlReader
import java.io.Serializable
import java.util.logging.Level

/**
 * Represents a WebDAV property.
 *
 * Every [Property] must define a static field (use `@JvmStatic`) called `NAME` of type [QName],
 * which will be accessed by reflection.
 */
interface Property {

    data class Name(
        val namespace: String,
        val name: String
    ) : Serializable {

        override fun toString() = "$namespace:$name"
    }

    companion object {

        fun parse(parser: XmlReader): List<Property> {
            // <!ELEMENT prop ANY >
            val properties = mutableListOf<Property>()

            XmlUtils.processTag(parser) {
                val name = parser.name

                try {
                    val property = PropertyRegistry.create(name, parser)

                    if (property != null) {
                        properties.add(property)
                    } else
                        log.fine("Ignoring unknown property $name")
                } catch (e: InvalidPropertyException) {
                    log.log(Level.WARNING, "Ignoring invalid property", e)
                }
            }

            return properties
        }
    }
}
