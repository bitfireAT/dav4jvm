/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.dav4android;

import android.text.TextUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.NonNull;

public class PropertyCollection {

    protected Map<String, Map<String, Property>> properties = null;


    public Property get(@NonNull Property.Name name) {
        if (properties == null)
            return null;

        Map<String, Property> nsProperties = properties.get(name.namespace);
        if (nsProperties == null)
            return null;

        return nsProperties.get(name.name);
    }

    public Map<Property.Name, Property> getMap() {
        HashMap<Property.Name, Property> map = new HashMap<>();
        if (properties != null) {
            for (String namespace : properties.keySet()) {
                Map<String, Property> nsProperties = properties.get(namespace);
                for (String name : nsProperties.keySet())
                    map.put(new Property.Name(namespace, name), nsProperties.get(name));
            }
        }
        return Collections.unmodifiableMap(map);
    }

    public void put(Property.Name name, Property property) {
        if (properties == null)
            properties = new HashMap<>();

        Map<String, Property> nsProperties = properties.get(name.namespace);
        if (nsProperties == null)
            properties.put(name.namespace, nsProperties = new HashMap<>());

        nsProperties.put(name.name, property);
    }

    public void remove(Property.Name name) {
        if (properties == null)
            return;

        Map<String, Property> nsProperties = properties.get(name.namespace);
        if (nsProperties != null)
            nsProperties.remove(name.name);
    }

    public int size() {
        if (properties == null)
            return 0;
        int size = 0;
        for (Map<String, Property> nsProperties : properties.values())
            size += nsProperties.size();
        return size;
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
    public void merge(PropertyCollection another, boolean removeNullValues) {
        Map<Property.Name, Property> properties = another.getMap();
        for (Property.Name name : properties.keySet()) {
            Property prop = properties.get(name);

            if (prop != null)
                put(name, prop);
            else {
                // prop == null
                if (removeNullValues)
                    remove(name);
                else if (get(name) == null)     // never overwrite non-null values
                    put(name, null);
            }
        }
    }

    public void nullAllValues() {
        if (properties == null)
            return;

        for (String namespace : properties.keySet()) {
            Map<String, Property> nsProperties = properties.get(namespace);
            if (nsProperties != null) {
                for (String name : nsProperties.keySet())
                    nsProperties.put(name, null);
            }
        }
    }


    @Override
    public String toString() {
        if (properties == null)
            return "[]";
        else {
            List<String> s = new LinkedList<>();
            Map<Property.Name, Property> properties = getMap();
            for (Property.Name name : properties.keySet()) {
                s.add(name + ": " + properties.get(name));
            }
            return "[" + TextUtils.join(",\n", s) + "]";
        }
    }

}
