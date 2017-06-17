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

    val properties = lazy { mutableMapOf<String, MutableMap<String, Property?>>() }


    operator fun get(name: Property.Name): Property? {
        if (!properties.isInitialized())
            return null

        val nsProperties = properties.value[name.namespace] ?: return null
        return nsProperties[name.name]
    }

    fun getMap(): Map<Property.Name, Property?> {
        if (!properties.isInitialized())
            return mapOf()

        val map = HashMap<Property.Name, Property?>()
        for ((namespace, nsProperties) in properties.value) {
            for ((name, property) in nsProperties)
                map[Property.Name(namespace, name)] = property
        }
        return unmodifiableMap(map)
    }

    operator fun set(name: Property.Name, property: Property?) {
        var nsProperties = properties.value[name.namespace]
        if (nsProperties == null) {
            nsProperties = mutableMapOf<String, Property?>()
            properties.value[name.namespace] = nsProperties
        }

        nsProperties[name.name] = property
    }

    operator fun minusAssign(name: Property.Name) {
        if (!properties.isInitialized())
            return

        val nsProperties = properties.value[name.namespace]
        nsProperties?.remove(name.name)
    }

    fun size(): Int {
        if (!properties.isInitialized())
            return 0

        var size = 0
        for (nsProperties in properties.value.values)
            size += nsProperties.size
        return size
    }


    /**
     * Merges another #{@link PropertyCollection} into #{@link #properties}.
     * Existing properties will be overwritten.
     * @param another           property collection to take the properties from
     * @param removeNullValues  Indicates how "another" properties with null values should be treated.
     *                          <ul>
     *                          <li>#{@code true}:  If the "another" property value is #{@code null}, the property will be removed in #{@link #properties}.</li>
     *                          <li>#{@code false}: If the "another" property value is #{@code null}, the property in #{@link #properties} will be set to null, too,
                                    but only if it doesn't exist yet. This means values in #{@link #properties} will never be overwritten by #{@code null}.</li>
     *                          </ul>
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

        for ((_, nsProperties) in properties.value) {
            for (name in nsProperties.keys)
                nsProperties.put(name, null)
        }
    }


    override fun toString(): String {
        val s = LinkedList<String>()
        for ((name, value) in getMap())
            s.add("$name: $value")
        return "[${s.joinToString(", ")}]"
    }

}
