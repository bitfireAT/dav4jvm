/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.dav4android.property;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Level;

import at.bitfire.dav4android.Constants;
import at.bitfire.dav4android.Property;
import at.bitfire.dav4android.PropertyFactory;
import at.bitfire.dav4android.XmlUtils;
import lombok.ToString;
import okhttp3.internal.http.HttpDate;

@ToString
public class GetLastModified implements Property {
    public static final Name NAME = new Name(XmlUtils.NS_WEBDAV, "getlastmodified");

    public Long lastModified;

    private GetLastModified() {}

    public GetLastModified(String rawDate)
    {
        Date date = HttpDate.parse(rawDate);
        if (date != null)
            lastModified = date.getTime();
        else
            Constants.log.warning("Couldn't parse Last-Modified date");
    }


    public static class Factory implements PropertyFactory {
        @Override
        public Name getName() {
            return NAME;
        }

        @Override
        public GetLastModified create(XmlPullParser parser) {
            // <!ELEMENT getlastmodified (#PCDATA) >
            try {
                return new GetLastModified(parser.nextText());
            } catch(XmlPullParserException|IOException e) {
                Constants.log.log(Level.SEVERE, "Couldn't parse <getlastmodified>", e);
                return null;
            }
        }
    }
}
