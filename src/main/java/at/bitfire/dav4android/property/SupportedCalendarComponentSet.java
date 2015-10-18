/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.dav4android.property;

import android.util.Log;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.Locale;

import at.bitfire.dav4android.Constants;
import at.bitfire.dav4android.Property;
import at.bitfire.dav4android.PropertyFactory;
import at.bitfire.dav4android.XmlUtils;
import lombok.ToString;

@ToString
public class SupportedCalendarComponentSet implements Property {
    public static final Name NAME = new Name(XmlUtils.NS_CALDAV, "supported-calendar-component-set");

    public boolean
            supportsEvents = false,
            supportsTasks = false;

    public static class Factory implements PropertyFactory {
        @Override
        public Name getName() {
            return NAME;
        }

        @Override
        public SupportedCalendarComponentSet create(XmlPullParser parser) {
            SupportedCalendarComponentSet components = new SupportedCalendarComponentSet();

            try {
                /* <!ELEMENT supported-calendar-component-set (comp+)>
                   <!ELEMENT comp ((allprop | prop*), (allcomp | comp*))>
                   <!ATTLIST comp name CDATA #REQUIRED>
                */
                final int depth = parser.getDepth();

                int eventType = parser.getEventType();
                while (!(eventType == XmlPullParser.END_TAG && parser.getDepth() == depth)) {
                    if (eventType == XmlPullParser.START_TAG && parser.getDepth() == depth+1 && XmlUtils.NS_CALDAV.equals(parser.getNamespace())) {
                        switch (parser.getName()) {
                            case "allcomp":
                                components.supportsEvents = components.supportsTasks = true;
                                break;
                            case "comp":
                                String compName = parser.getAttributeValue(null, "name");
                                if (compName != null)
                                    switch (compName.toUpperCase(Locale.US)) {
                                        case "VEVENT":
                                            components.supportsEvents = true;
                                            break;
                                        case "VTODO":
                                            components.supportsTasks = true;
                                            break;
                                    }
                                break;
                        }
                    }
                    eventType = parser.next();
                }
            } catch(XmlPullParserException|IOException e) {
                Log.e(Constants.LOG_TAG, "Couldn't parse <supported-calendar-component-set>", e);
                return null;
            }

            return components;
        }
    }
}
