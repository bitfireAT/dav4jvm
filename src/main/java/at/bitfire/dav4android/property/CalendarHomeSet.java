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

public class CalendarHomeSet implements Property {
    public static final Name NAME = new Name(XmlUtils.NS_CALDAV, "calendar-home-set");

    public List<String> hrefs = new LinkedList<>();

    @Override
    public String toString() {
        return "hrefs=[" + TextUtils.join(", ", hrefs) + "]";
    }


    public static class Factory implements PropertyFactory {
        @Override
        public Name getName() {
            return NAME;
        }

        @Override
        public CalendarHomeSet create(XmlPullParser parser) {
            CalendarHomeSet homeSet = new CalendarHomeSet();

            try {
                // <!ELEMENT calendar-home-set (DAV:href*)>
                final int depth = parser.getDepth();

                int eventType = parser.getEventType();
                while (!(eventType == XmlPullParser.END_TAG && parser.getDepth() == depth)) {
                    if (eventType == XmlPullParser.START_TAG && parser.getDepth() == depth+1 &&
                            XmlUtils.NS_WEBDAV.equals(parser.getNamespace()) && "href".equals(parser.getName()))
                        homeSet.hrefs.add(parser.nextText());
                    eventType = parser.next();
                }
            } catch(XmlPullParserException|IOException e) {
                Log.e(Constants.LOG_TAG, "Couldn't parse <calendar-home-set>", e);
                return null;
            }

            return homeSet;
        }
    }
}
