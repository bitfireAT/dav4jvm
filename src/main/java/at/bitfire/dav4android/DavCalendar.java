/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.dav4android;

import com.squareup.okhttp.HttpUrl;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;

import org.slf4j.Logger;
import org.xmlpull.v1.XmlSerializer;

import java.io.IOException;
import java.io.StringWriter;

import at.bitfire.dav4android.exception.DavException;
import at.bitfire.dav4android.exception.HttpException;

public class DavCalendar extends DavResource {

    public static final MediaType
            MIME_ICALENDAR = MediaType.parse("text/calendar;charset=utf-8");

    public DavCalendar(Logger log, OkHttpClient httpClient, HttpUrl location) {
        super(log, httpClient, location);
    }

    public void calendarQuery(String component) throws IOException, HttpException, DavException {
        /* <!ELEMENT calendar-query ((DAV:allprop |
                                      DAV:propname |
                                      DAV:prop)?, filter, timezone?)>
           <!ELEMENT filter (comp-filter)>
           <!ELEMENT comp-filter (is-not-defined | (time-range?,
                                  prop-filter*, comp-filter*))>
           <!ATTLIST comp-filter name CDATA #REQUIRED>
           name value: a calendar object or calendar component
                       type (e.g., VEVENT)

        */
        XmlSerializer serializer = XmlUtils.newSerializer();
        StringWriter writer = new StringWriter();
        serializer.setOutput(writer);
        serializer.startDocument("UTF-8", null);
        serializer.setPrefix("", XmlUtils.NS_WEBDAV);
        serializer.setPrefix("CAL", XmlUtils.NS_CALDAV);
        serializer.startTag(XmlUtils.NS_CALDAV, "calendar-query");
            serializer.startTag(XmlUtils.NS_WEBDAV, "prop");
                serializer.startTag(XmlUtils.NS_WEBDAV, "getetag");
                serializer.endTag(XmlUtils.NS_WEBDAV, "getetag");
            serializer.endTag(XmlUtils.NS_WEBDAV,   "prop");
            serializer.startTag(XmlUtils.NS_CALDAV, "filter");
                serializer.startTag(XmlUtils.NS_CALDAV, "comp-filter");
                serializer.attribute(null, "name", "VCALENDAR");
                    serializer.startTag(XmlUtils.NS_CALDAV, "comp-filter");
                    serializer.attribute(null, "name", component);
                    serializer.endTag(XmlUtils.NS_CALDAV, "comp-filter");
                serializer.endTag(XmlUtils.NS_CALDAV,   "comp-filter");
            serializer.endTag(XmlUtils.NS_CALDAV,   "filter");
        serializer.endTag(XmlUtils.NS_CALDAV,   "calendar-query");
        serializer.endDocument();

        // redirects must not followed automatically (as it may rewrite REPORT requests to GET requests)
        httpClient.setFollowRedirects(false);

        Response response = httpClient.newCall(new Request.Builder()
                .url(location)
                .method("REPORT", RequestBody.create(MIME_XML, writer.toString()))
                .header("Depth", "1")
                .build()).execute();

        checkStatus(response);
        assertMultiStatus(response);

        members.clear();
        processMultiStatus(response.body().charStream());
    }

    public void multiget(HttpUrl[] urls) throws IOException, HttpException, DavException {
        /* <!ELEMENT calendar-multiget ((DAV:allprop |
                                        DAV:propname |
                                        DAV:prop)?, DAV:href+)>
        */
        XmlSerializer serializer = XmlUtils.newSerializer();
        StringWriter writer = new StringWriter();
        serializer.setOutput(writer);
        serializer.startDocument("UTF-8", null);
        serializer.setPrefix("", XmlUtils.NS_WEBDAV);
        serializer.setPrefix("CAL", XmlUtils.NS_CALDAV);
        serializer.startTag(XmlUtils.NS_CALDAV, "calendar-multiget");
        serializer.startTag(XmlUtils.NS_WEBDAV, "prop");
        serializer.startTag(XmlUtils.NS_WEBDAV, "getcontenttype");      // to determine the character set
        serializer.endTag(XmlUtils.NS_WEBDAV, "getcontenttype");
        serializer.startTag(XmlUtils.NS_WEBDAV, "getetag");
        serializer.endTag(XmlUtils.NS_WEBDAV, "getetag");
        serializer.startTag(XmlUtils.NS_CALDAV, "calendar-data");
        serializer.endTag(XmlUtils.NS_CALDAV, "calendar-data");
        serializer.endTag(XmlUtils.NS_WEBDAV, "prop");
        for (HttpUrl url : urls) {
            serializer.startTag(XmlUtils.NS_WEBDAV, "href");
            serializer.text(url.encodedPath());
            serializer.endTag(XmlUtils.NS_WEBDAV, "href");
        }
        serializer.endTag(XmlUtils.NS_CALDAV, "calendar-multiget");
        serializer.endDocument();

        // redirects must not followed automatically (as it may rewrite REPORT requests to GET requests)
        httpClient.setFollowRedirects(false);

        Response response = httpClient.newCall(new Request.Builder()
                .url(location)
                .method("REPORT", RequestBody.create(MIME_XML, writer.toString()))
                .build()).execute();

        checkStatus(response);
        assertMultiStatus(response);

        members.clear();
        processMultiStatus(response.body().charStream());
    }

}
