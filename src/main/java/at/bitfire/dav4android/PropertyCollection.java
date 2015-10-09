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


    Property get(@NonNull Property.Name name) {
        if (properties == null)
            return null;

        Map<String, Property> nsProperties = properties.get(name.namespace);
        if (nsProperties == null)
            return null;

        return nsProperties.get(name.name);
    }

    Map<Property.Name, Property> getMap() {
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

    void put(Property.Name name, Property property) {
        if (properties == null)
            properties = new HashMap<>();

        Map<String, Property> nsProperties = properties.get(name.namespace);
        if (nsProperties == null)
            properties.put(name.namespace, nsProperties = new HashMap<>());

        nsProperties.put(name.name, property);
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
