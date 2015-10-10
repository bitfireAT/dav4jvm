/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.dav4android.property;

import android.text.TextUtils;
import android.util.Log;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import at.bitfire.dav4android.Constants;
import at.bitfire.dav4android.Property;
import at.bitfire.dav4android.PropertyFactory;
import at.bitfire.dav4android.XmlUtils;

public class CurrentUserPrivilegeSet implements Property {
    public static final Name NAME = new Name(XmlUtils.NS_WEBDAV, "current-user-privilege-set");

    // only those privileges which are required for DAVdroid are implemented
    public boolean
            mayRead,
            mayWriteContent;

    @Override
    public String toString() {
        List<String> s = new LinkedList<>();
        if (mayRead)
            s.add("read");
        if (mayWriteContent)
            s.add("write");
        return "[" + TextUtils.join("/", s) + "]";
    }

    public static class Factory implements PropertyFactory {
        @Override
        public Name getName() {
            return NAME;
        }

        @Override
        public CurrentUserPrivilegeSet create(XmlPullParser parser) {
            CurrentUserPrivilegeSet privs = new CurrentUserPrivilegeSet();

            try {
                // <!ELEMENT current-user-privilege-set (privilege*)>
                final int depth = parser.getDepth();

                int eventType = parser.getEventType();
                while (!(eventType == XmlPullParser.END_TAG && parser.getDepth() == depth)) {
                    if (eventType == XmlPullParser.START_TAG && parser.getDepth() == depth+1 &&
                            XmlUtils.NS_WEBDAV.equals(parser.getNamespace()) && "privilege".equals(parser.getName()))
                        parsePrivilege(parser, privs);
                    eventType = parser.next();
                }
            } catch(XmlPullParserException|IOException e) {
                Log.e(Constants.LOG_TAG, "Couldn't parse <current-user-privilege-set>", e);
                return null;
            }

            return privs;
        }

        protected void parsePrivilege(XmlPullParser parser, CurrentUserPrivilegeSet privs) throws XmlPullParserException, IOException {
            final int depth = parser.getDepth();
            // <!ELEMENT privilege ANY>

            int eventType = parser.getEventType();
            while (!(eventType == XmlPullParser.END_TAG && parser.getDepth() == depth)) {
                if (eventType == XmlPullParser.START_TAG && parser.getDepth() == depth+1 && XmlUtils.NS_WEBDAV.equals(parser.getNamespace())) {
                    String name = parser.getName();
                    switch (name) {
                        case "read":
                            privs.mayRead = true;
                            break;
                        case "write":
                        case "write-content":
                            privs.mayWriteContent = true;
                            break;
                        case "all":
                            privs.mayRead = privs.mayWriteContent = true;
                            break;
                    }
                }
                eventType = parser.next();
            }
        }
    }
}
