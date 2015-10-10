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

import at.bitfire.dav4android.Constants;
import at.bitfire.dav4android.Property;
import at.bitfire.dav4android.PropertyFactory;
import at.bitfire.dav4android.XmlUtils;
import lombok.ToString;

// see RFC 5397: WebDAV Current Principal Extension

@ToString
public class CurrentUserPrincipal implements Property {
    public static final Name NAME = new Name(XmlUtils.NS_WEBDAV, "current-user-principal");

    public String href;


    public static class Factory implements PropertyFactory {
        @Override
        public Name getName() {
            return NAME;
        }

        @Override
        public CurrentUserPrincipal create(XmlPullParser parser) {
            CurrentUserPrincipal principal = new CurrentUserPrincipal();

            try {
                // <!ELEMENT current-user-principal (unauthenticated | href)>
                final int depth = parser.getDepth();

                int eventType = parser.getEventType();
                while (!(eventType == XmlPullParser.END_TAG && parser.getDepth() == depth)) {
                    if (eventType == XmlPullParser.START_TAG && parser.getDepth() == depth+1 &&
                            XmlUtils.NS_WEBDAV.equals(parser.getNamespace()) && "href".equals(parser.getName()))
                        principal.href = parser.nextText();
                    eventType = parser.next();
                }
            } catch(XmlPullParserException|IOException e) {
                Log.e(Constants.LOG_TAG, "Couldn't parse <current-user-principal>", e);
                return null;
            }

            return principal;
        }
    }
}
