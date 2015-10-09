/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.dav4android;

import android.util.Log;

import com.squareup.okhttp.HttpUrl;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.internal.http.StatusLine;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmlpull.v1.XmlSerializer;

import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;

import at.bitfire.dav4android.exception.DavException;
import at.bitfire.dav4android.exception.HttpException;
import at.bitfire.dav4android.exception.InvalidDavResponseException;
import at.bitfire.dav4android.exception.UnsupportedDavException;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

@RequiredArgsConstructor
public class DavResource {

    public final MediaType MEDIA_TYPE_XML = MediaType.parse("application/xml; charset=utf-8");

    protected final OkHttpClient httpClient;

    final HttpUrl location;
    final PropertyCollection properties = new PropertyCollection();

    static private PropertyRegistry registry = PropertyRegistry.DEFAULT;


    @SneakyThrows(XmlPullParserException.class)
    public void propfind(Property.Name... reqProp) throws IOException, HttpException, DavException {
        // build XML request body
        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        XmlSerializer serializer = factory.newSerializer();
        StringWriter writer = new StringWriter();
        serializer.setOutput(writer);
        serializer.startDocument("UTF-8", null);
        serializer.startTag(XmlUtils.NS_WEBDAV, "propfind");
        serializer.startTag(XmlUtils.NS_WEBDAV, "prop");
        for (Property.Name prop : reqProp) {
            serializer.startTag(prop.namespace, prop.name);
            serializer.endTag(prop.namespace, prop.name);
        }
        serializer.endTag(XmlUtils.NS_WEBDAV, "prop");
        serializer.endTag(XmlUtils.NS_WEBDAV, "propfind");
        serializer.endDocument();

        Response response = httpClient.newCall(new Request.Builder()
                .url(location)
                .method("PROPFIND", RequestBody.create(MEDIA_TYPE_XML, writer.toString()))
                .header("Depth", "0")
                .build()).execute();

        checkStatus(response);

        if (response.code() != 207)
            throw new InvalidDavResponseException("Expected 207 Multi-Status");

        if (response.body() == null)
            throw new InvalidDavResponseException("Received 207 Multi-Status without body");

        MediaType mediaType = response.body().contentType();
        if (mediaType != null) {
            String type = mediaType.type() + "/" + mediaType.subtype();
            if (!"application/xml".equals(type) || "text/xml".equals(type))
                throw new InvalidDavResponseException("Received non-XML 207 Multi-Status");
        }

        processMultiStatus(response.body().charStream());
    }


    protected void checkStatus(int code, String message) throws HttpException {
        if (code/100 == 1 || code/100 == 2)
            // everything OK
            return;

        throw new HttpException(code, message);
    }

    protected void checkStatus(Response response) throws HttpException {
        checkStatus(response.code(), response.message());
    }

    protected void checkStatus(StatusLine status) throws HttpException {
        checkStatus(status.code, status.message);
    }


    protected void processMultiStatus(Reader reader) throws IOException, HttpException, DavException {
        XmlPullParser parser = XmlUtils.newPullParser();
        try {
            parser.setInput(reader);

            boolean multiStatus = false;

            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.getDepth() == 1) {
                    final String ns = parser.getNamespace(), name = parser.getName();
                    if (XmlUtils.NS_WEBDAV.equals(ns) && "multistatus".equals(name)) {
                        parseMultiStatus(parser);
                        multiStatus = true;
                    }
                }
                eventType = parser.next();
            }

            if (!multiStatus)
                throw new InvalidDavResponseException("Multi-Status response didn't contain <DAV:multistatus> root element");

        } catch (XmlPullParserException e) {
            throw new InvalidDavResponseException("Couldn't parse Multi-Status XML", e);
        }
    }

    private void parseMultiStatus(XmlPullParser parser) throws IOException, XmlPullParserException, HttpException, DavException {
        // <!ELEMENT multistatus (response*, responsedescription?)  >
        final int depth = parser.getDepth();

        int eventType = parser.getEventType();
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.getDepth() == depth+1) {
                String ns = parser.getNamespace(), name = parser.getName();
                if (XmlUtils.NS_WEBDAV.equals(ns) && "response".equals(name))
                    parseMultiStatus_Response(parser);

            } else if (eventType == XmlPullParser.END_TAG && parser.getDepth() == depth)
                break;
            eventType = parser.next();
        }
    }

    private void parseMultiStatus_Response(XmlPullParser parser) throws IOException, XmlPullParserException, HttpException, UnsupportedDavException {
        /* <!ELEMENT response (href, ((href*, status)|(propstat+)),
                                       error?, responsedescription? , location?) > */
        final int depth = parser.getDepth();

        HttpUrl href = null;
        StatusLine status = null;
        PropertyCollection properties = null;

        int eventType = parser.getEventType();
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.getDepth() == depth+1) {
                String ns = parser.getNamespace(), name = parser.getName();
                if (XmlUtils.NS_WEBDAV.equals(ns))
                    switch (name) {
                        case "href":
                            href = location.resolve(parser.nextText());
                            break;
                        case "status":
                            status = StatusLine.parse(parser.nextText());
                            break;
                        case "propstat":
                            PropertyCollection prop = parseMultiStatus_PropStat(parser);
                            if (prop != null)
                                properties = prop;
                            break;
                        case "location":
                            throw new UnsupportedDavException("Redirected child resources are not supported yet");
                    }
            } else if (eventType == XmlPullParser.END_TAG && parser.getDepth() == depth)
                break;
            eventType = parser.next();
        }

        if (href == null) {
            Log.w(Constants.LOG_TAG, "Ignoring invalid <response> without <href>");
            return;
        }

        if (status != null)
            // treat an HTTP error of a single response (i.e. requested resource or a member) like an HTTP error of the requested resource
            checkStatus(status);

        Log.d(Constants.LOG_TAG, "Received <response> for " + href + ", status: " + status + ", properties: " + properties);
    }

    private PropertyCollection parseMultiStatus_PropStat(XmlPullParser parser) throws IOException, XmlPullParserException {
        // <!ELEMENT propstat (prop, status, error?, responsedescription?) >
        final int depth = parser.getDepth();

        StatusLine status = null;
        PropertyCollection prop = null;

        int eventType = parser.getEventType();
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.getDepth() == depth+1) {
                String ns = parser.getNamespace(), name = parser.getName();
                if (XmlUtils.NS_WEBDAV.equals(ns))
                    switch (name) {
                        case "prop":
                            prop = parseMultiStatus_Prop(parser);
                            break;
                        case "status":
                            status = StatusLine.parse(parser.nextText());
                    }
            } else if (eventType == XmlPullParser.END_TAG && parser.getDepth() == depth)
                break;
            eventType = parser.next();
        }

        if (status == null || status.code/100 == 2)
            return prop;

        return null;
    }

    private PropertyCollection parseMultiStatus_Prop(XmlPullParser parser) throws IOException, XmlPullParserException {
        // <!ELEMENT prop ANY >
        final int depth = parser.getDepth();

        PropertyCollection prop = new PropertyCollection();

        int eventType = parser.getEventType();
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.getDepth() == depth+1) {
                Property.Name name = new Property.Name(parser.getNamespace(), parser.getName());
                Property property = registry.create(name, parser);
                if (property != null)
                    prop.put(name, property);
                else
                    Log.i(Constants.LOG_TAG, "Ignoring unknown/unparseable property " + name);
            }
            eventType = parser.next();
        }

        return prop;
    }

}
