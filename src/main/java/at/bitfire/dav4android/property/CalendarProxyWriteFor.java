/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.dav4android.property;

import android.text.TextUtils;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Level;

import at.bitfire.dav4android.Constants;
import at.bitfire.dav4android.Property;
import at.bitfire.dav4android.PropertyFactory;
import at.bitfire.dav4android.XmlUtils;

public class CalendarProxyWriteFor implements Property {
    public static final Name NAME = new Name(XmlUtils.NS_CALENDARSERVER, "calendar-proxy-write-for");

    public final List<String> principals = new LinkedList<>();

    @Override
    public String toString() {
        return "hrefs=[" + TextUtils.join(", ", principals) + "]";
    }


    public static class Factory implements PropertyFactory {
        @Override
        public Name getName() {
            return NAME;
        }

        @Override
        public CalendarProxyWriteFor create(XmlPullParser parser) {
            CalendarProxyWriteFor proxyRead = new CalendarProxyWriteFor();

            try {
                // <!ELEMENT calendar-proxy-read-for (DAV:href*)>
                final int depth = parser.getDepth();

                int eventType = parser.getEventType();
                while (!(eventType == XmlPullParser.END_TAG && parser.getDepth() == depth)) {
                    if (eventType == XmlPullParser.START_TAG && parser.getDepth() == depth+1 &&
                            XmlUtils.NS_WEBDAV.equals(parser.getNamespace()) && "href".equals(parser.getName()))
                        proxyRead.principals.add(parser.nextText());
                    eventType = parser.next();
                }
            } catch(XmlPullParserException|IOException e) {
                Constants.log.log(Level.SEVERE, "Couldn't parse <calendar-proxy-write-for>", e);
                return null;
            }

            return proxyRead;
        }
    }
}
