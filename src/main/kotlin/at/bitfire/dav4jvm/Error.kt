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

import org.xmlpull.v1.XmlPullParser
import java.io.Serializable

/**
 * Represents an XML precondition/postcondition error. Every error has a name, which is the XML element
 * name. Subclassed errors may have more specific information available.
 *
 * At the moment, there is no logic for subclassing errors.
 *
 * @param name  property name for the XML error element
 */
data class Error(
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

    }

    override fun equals(other: Any?) =
        (other is Error) && other.name == name

    override fun hashCode() = name.hashCode()

}