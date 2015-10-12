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

import at.bitfire.dav4android.Constants;
import at.bitfire.dav4android.Property;
import at.bitfire.dav4android.PropertyFactory;
import at.bitfire.dav4android.StringUtils;
import at.bitfire.dav4android.XmlUtils;
import lombok.ToString;

@ToString
public class GetETag implements Property {
    public static final Name NAME = new Name(XmlUtils.NS_WEBDAV, "getetag");

    public String eTag;

    private GetETag() {}

    public GetETag(String rawETag)
    {
        /* entity-tag = [ weak ] opaque-tag
           weak       = "W/"
           opaque-tag = quoted-string
        */

        // remove trailing "W/"
        if (rawETag.startsWith("W/") && rawETag.length() >= 3)
            // entity tag is weak (doesn't matter for us)
            rawETag = rawETag.substring(2);

        eTag = StringUtils.decodeQuotedString(rawETag);
    }


    public static class Factory implements PropertyFactory {
        @Override
        public Name getName() {
            return NAME;
        }

        @Override
        public GetETag create(XmlPullParser parser) {
            // <!ELEMENT getetag (#PCDATA) >
            // ETag = "ETag" ":" entity-tag
            try {
                return new GetETag(parser.nextText());
            } catch(XmlPullParserException|IOException e) {
                Constants.log.error("Couldn't parse <getetag>", e);
                return null;
            }
        }
    }
}
