/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.dav4android

/**
 * A WebDAV property.
 *
 * Every [Property] must define a static field called NAME of type Property.Name,
 * which will be accessed by reflection.
 */
interface Property {

    class Name(
            val namespace: String,
            val name: String
    ) {

        override fun equals(o: Any?): Boolean {
            return if (o is Name)
                namespace == o.namespace && name == o.name
            else
                super.equals(o)
        }

        override fun hashCode() = namespace.hashCode() xor name.hashCode()

        override fun toString() = "$namespace$name"
    }

}
