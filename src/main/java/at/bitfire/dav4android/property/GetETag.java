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

@ToString
public class GetETag implements Property {
    public static final Name NAME = new Name(XmlUtils.NS_WEBDAV, "getetag");

    public String eTag;

    private GetETag() {}

    public GetETag(String eTag) {
        this.eTag = eTag;
    }


    public static class Factory implements PropertyFactory {
        @Override
        public Name getName() {
            return NAME;
        }

        @Override
        public GetETag create(XmlPullParser parser) {
            GetETag getETag = new GetETag();

            try {
                int eventType = parser.getEventType();
                getETag.eTag = parser.nextText();
            } catch(XmlPullParserException |IOException e) {
                Log.e(Constants.LOG_TAG, "Couldn't parse <getetag>", e);
                return null;
            }

            return getETag;
        }
    }
}
