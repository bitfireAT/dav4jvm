/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.dav4android

import java.util.*
import java.util.Collections.unmodifiableMap

class PropertyCollection {

    val properties = lazy { mutableMapOf<Property.Name, Property?>() }


    /**
     * Returns a WebDAV property, or null if this property is not known.
     * In most cases, using the alternative [get] with [Class] parameter is better because it provides type safety.
     */
    operator fun get(name: Property.Name): Property? =
            if (!properties.isInitialized())
                null
            else
                properties.value[name]

    /**
     * Returns a WebDAV property, or null if this property is not known.
     */
    operator fun<T: Property> get(clazz: Class<T>): T? {
        if (!properties.isInitialized())
            return null

        return try {
            val name = clazz.getDeclaredField("NAME").get(null) as Property.Name
            properties.value[name] as? T
        } catch (e: NoSuchFieldException) {
            Constants.log.severe("$clazz does not have a static NAME field")
            null
        }
    }

    fun getMap(): Map<Property.Name, Property?> =
            if (!properties.isInitialized())
                mapOf()
            else
                unmodifiableMap(properties.value)

    operator fun set(name: Property.Name, property: Property?) {
        properties.value[name] = property
    }

    operator fun minusAssign(name: Property.Name) {
        if (!properties.isInitialized())
            return
        properties.value.remove(name)
    }

    fun size() =
            if (!properties.isInitialized())
                0
            else
                properties.value.size


    /**
     * Merges another [PropertyCollection] into [properties].
     * Existing properties will be overwritten.
     *
     * @param another           property collection to take the properties from
     * @param removeNullValues  Indicates how "another" properties with null values should be treated.
     *   - true: If the "another" property value is null, the property will be removed in [properties].
     *   - false: If the "another" property value is null, the property in [properties] will be set to null, too,
     *   but only if it doesn't exist yet. This means values in [properties] will never be overwritten by null.
     */
    fun merge(another: PropertyCollection, removeNullValues: Boolean) {
        val properties = another.getMap()
        for ((name, prop) in properties) {
            if (prop != null)
                set(name, prop)
            else {
                // prop == null
                if (removeNullValues)
                    this -= name
                else if (get(name) == null)     // never overwrite non-null values
                    set(name, null)
            }
        }
    }

    fun nullAllValues() {
        if (!properties.isInitialized())
            return

        val props = properties.value
        for (name in props.keys)
            props[name] = null
    }


    override fun toString(): String {
        val s = LinkedList<String>()
        for ((name, value) in getMap())
            s.add("$name = $value")
        return "[${s.joinToString(", ")}]"
    }

}
