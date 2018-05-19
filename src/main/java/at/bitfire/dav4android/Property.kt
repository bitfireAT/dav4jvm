/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4android

/**
 * A WebDAV property.
 *
 * Every [Property] must define a static field (use @JvmStatic) called NAME of type [Property.Name],
 * which will be accessed by reflection.
 */
interface Property {

    class Name(
            val namespace: String,
            val name: String
    ) {

        override fun equals(other: Any?): Boolean {
            return if (other is Name)
                namespace == other.namespace && name == other.name
            else
                super.equals(other)
        }

        override fun hashCode() = namespace.hashCode() xor name.hashCode()

        override fun toString() = "$namespace$name"
    }

}
