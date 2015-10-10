/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.dav4android;

import android.util.Log;

import com.squareup.okhttp.Credentials;
import com.squareup.okhttp.HttpUrl;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Protocol;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.internal.http.StatusLine;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.net.ProtocolException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import at.bitfire.dav4android.exception.DavException;
import at.bitfire.dav4android.exception.HttpException;
import at.bitfire.dav4android.exception.InvalidDavResponseException;
import at.bitfire.dav4android.exception.UnsupportedDavException;
import at.bitfire.dav4android.property.ResourceType;

public class DavResource {

    public final MediaType MEDIA_TYPE_XML = MediaType.parse("application/xml; charset=utf-8");

    protected final OkHttpClient httpClient;

    public HttpUrl location;
    public final PropertyCollection properties = new PropertyCollection();
    public final Set<DavResource> members = new HashSet<>();

    static private PropertyRegistry registry = PropertyRegistry.DEFAULT;

    public DavResource(OkHttpClient httpClient, HttpUrl location) {
        this.httpClient = httpClient;
        this.location = location;
    }


    public void propfind(int depth, Property.Name... reqProp) throws IOException, HttpException, DavException {
        // build XML request body
        XmlSerializer serializer = XmlUtils.newSerializer();
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

        // redirects must not followed automatically (as it may rewrite PROPFIND requests to GET requests)
        httpClient.setFollowRedirects(false);

        Response response = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            Constants.log.info("Attempt " + attempt);
            response = httpClient.newCall(new Request.Builder()
                    .url(location)
                    .method("PROPFIND", RequestBody.create(MEDIA_TYPE_XML, writer.toString()))
                    .header("Depth", String.valueOf(depth))
                    .header("Authorization", Credentials.basic("XXXXXXXXXXXX", "XXXXXXXXx"))    // TODO
                    .build()).execute();

            if (response.code()/100 == 3) {
                String href = response.header("Location");
                if (href != null) {
                    HttpUrl newLocation = location.resolve(href);
                    if (newLocation != null)
                        location = newLocation;
                    else
                        throw new HttpException(500, "Redirect without Location");
                }
            } else
                break;
        }

        checkStatus(response);

        if (response.code() != 207)
            throw new InvalidDavResponseException("Expected 207 Multi-Status");

        if (response.body() == null)
            throw new InvalidDavResponseException("Received multi-status response without body");

        MediaType mediaType = response.body().contentType();
        if (mediaType != null) {
            if (!("application".equals(mediaType.type()) || "text".equals(mediaType.type())) ||
                    !"xml".equals(mediaType.subtype()))
                throw new InvalidDavResponseException("Received non-XML 207 Multi-Status");
        } else
            Constants.log.warn("Received multi-status response without Content-Type, assuming XML");

        if (depth > 0)
            // collection listing requested, drop old member information
            members.clear();

        processMultiStatus(response.body().charStream());
    }


    protected void checkStatus(int code, String message) throws HttpException {
        if (code/100 == 2)
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
        while (!(eventType == XmlPullParser.END_TAG && parser.getDepth() == depth)) {
            if (eventType == XmlPullParser.START_TAG && parser.getDepth() == depth+1 &&
                    XmlUtils.NS_WEBDAV.equals(parser.getNamespace()) && "response".equals(parser.getName()))
                parseMultiStatus_Response(parser);
            eventType = parser.next();
        }
    }

    private void parseMultiStatus_Response(XmlPullParser parser) throws IOException, XmlPullParserException, HttpException, UnsupportedDavException {
        /* <!ELEMENT response (href, ((href*, status)|(propstat+)),
                                       error?, responsedescription? , location?) > */
        final int depth = parser.getDepth();

        HttpUrl href = null;
        StatusLine status = null;
        PropertyCollection properties = new PropertyCollection();

        int eventType = parser.getEventType();
        while (!(eventType == XmlPullParser.END_TAG && parser.getDepth() == depth)) {
            if (eventType == XmlPullParser.START_TAG && parser.getDepth() == depth+1) {
                String ns = parser.getNamespace(), name = parser.getName();
                if (XmlUtils.NS_WEBDAV.equals(ns))
                    switch (name) {
                        case "href":
                            String sHref = parser.nextText();
                            if (!sHref.startsWith("/")) {
                                /* According to RFC 4918 8.3 URL Handling, only absolute paths are allowed as relative
                                   URLs. However, some servers reply with relative paths. */
                                int firstColon = sHref.indexOf(':');
                                if (firstColon != -1) {
                                    /* There are some servers which return not only relative paths, but relative paths like "a:b.vcf",
                                       which would be interpreted as scheme: "a", scheme-specific part: "b.vcf" normally.
                                       For maximum compatibility, we prefix all relative paths which contain ":" (but not "://"),
                                       with "./" to allow resolving by HttpUrl. */
                                    boolean hierarchical = false;
                                    try {
                                        if ("://".equals(sHref.substring(firstColon, firstColon + 3)))
                                            hierarchical = true;
                                    } catch (IndexOutOfBoundsException e) {
                                        // no "://"
                                    }
                                    if (!hierarchical)
                                        sHref = "./" + sHref;
                                }
                            }
                            href = location.resolve(sHref);
                            break;
                        case "status":
                            try {
                                status = StatusLine.parse(parser.nextText());
                            } catch(ProtocolException e) {
                                Constants.log.warn("Invalid status line, treating as 500 Server Error");
                                status = new StatusLine(Protocol.HTTP_1_1, 500, "Invalid status line");
                            }
                            break;
                        case "propstat":
                            PropertyCollection prop = parseMultiStatus_PropStat(parser);
                            if (prop != null)
                                properties.merge(prop);
                            break;
                        case "location":
                            throw new UnsupportedDavException("Redirected child resources are not supported yet");
                    }
            }
            eventType = parser.next();
        }

        if (href == null) {
            Constants.log.warn("Ignoring <response> without valid <href>");
            return;
        }

        // if we know this resource is a collection, make sure href has a trailing slash (for clarity and resolving relative paths)
        ResourceType type = (ResourceType)properties.get(ResourceType.NAME);
        if (type != null && type.types.contains(ResourceType.COLLECTION))
            href = UrlUtils.withTrailingSlash(href);

        Constants.log.debug("Received <response> for " + href + ", status: " + status + ", properties: " + properties);

        if (status != null)
            // treat an HTTP error of a single response (i.e. requested resource or a member) like an HTTP error of the requested resource
            checkStatus(status);

        // Which resource does this <response> represent?
        DavResource target = null;
        if (UrlUtils.omitTrailingSlash(href).equals(UrlUtils.omitTrailingSlash(location))) {
            // it's about ourselves
            target = this;
        } else if (location.scheme().equals(href.scheme()) && location.host().equals(href.host()) && location.port() == href.port()) {
            List<String> locationSegments = location.pathSegments(), hrefSegments = href.pathSegments();

            // don't compare trailing slash segment ("")
            int nBasePathSegments = locationSegments.size();
            if ("".equals(locationSegments.get(nBasePathSegments-1)))
                nBasePathSegments--;

            /* example:   locationSegments  = [ "davCollection", "" ]
                          nBasePathSegments = 1
                          hrefSegments      = [ "davCollection", "aMember" ]
            */

            if (hrefSegments.size() > nBasePathSegments) {
                boolean sameBasePath = true;
                for (int i = 0; i < nBasePathSegments; i++) {
                    if (!locationSegments.get(i).equals(hrefSegments.get(i))) {
                        sameBasePath = false;
                        break;
                    }
                }

                if (sameBasePath)
                    members.add(target = new DavResource(httpClient, href));
            }
        }

        // set properties for target
        if (target != null)
            target.properties.merge(properties);
        else
            Constants.log.warn("Received <response> for resource that was not requested");
    }

    private PropertyCollection parseMultiStatus_PropStat(XmlPullParser parser) throws IOException, XmlPullParserException {
        // <!ELEMENT propstat (prop, status, error?, responsedescription?) >
        final int depth = parser.getDepth();

        StatusLine status = null;
        PropertyCollection prop = null;

        int eventType = parser.getEventType();
        while (!(eventType == XmlPullParser.END_TAG && parser.getDepth() == depth)) {
            if (eventType == XmlPullParser.START_TAG && parser.getDepth() == depth+1) {
                String ns = parser.getNamespace(), name = parser.getName();
                if (XmlUtils.NS_WEBDAV.equals(ns))
                    switch (name) {
                        case "prop":
                            prop = parseMultiStatus_Prop(parser);
                            break;
                        case "status":
                            try {
                                status = StatusLine.parse(parser.nextText());
                            } catch(ProtocolException e) {
                                Constants.log.warn("Invalid status line, treating as 500 Server Error");
                                status = new StatusLine(Protocol.HTTP_1_1, 500, "Invalid status line");
                            }
                    }
            }
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
        while (!(eventType == XmlPullParser.END_TAG && parser.getDepth() == depth)) {
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
