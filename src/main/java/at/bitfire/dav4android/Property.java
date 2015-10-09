/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.dav4android;

import android.provider.UserDictionary;
import android.text.TextUtils;

import lombok.RequiredArgsConstructor;

public interface Property {

    @RequiredArgsConstructor
    class Name {
        public final String namespace;
        public final String name;

        @Override
        public boolean equals(Object o) {
            if (o instanceof Name) {
                Name n = (Name)o;
                return  TextUtils.equals(namespace, n.namespace) &&
                        TextUtils.equals(name, n.name);
            } else
                return super.equals(o);
        }

        @Override
        public String toString() {
            return name + "(" + namespace + ")";
        }
    }

}
