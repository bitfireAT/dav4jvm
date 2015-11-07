/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.dav4android;

import android.text.TextUtils;

import com.squareup.okhttp.HttpUrl;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Protocol;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.ResponseBody;
import com.squareup.okhttp.internal.http.StatusLine;

import org.slf4j.Logger;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlSerializer;

import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import at.bitfire.dav4android.exception.ConflictException;
import at.bitfire.dav4android.exception.DavException;
import at.bitfire.dav4android.exception.HttpException;
import at.bitfire.dav4android.exception.InvalidDavResponseException;
import at.bitfire.dav4android.exception.NotFoundException;
import at.bitfire.dav4android.exception.PreconditionFailedException;
import at.bitfire.dav4android.exception.ServiceUnavailableException;
import at.bitfire.dav4android.exception.UnauthorizedException;
import at.bitfire.dav4android.exception.UnsupportedDavException;
import at.bitfire.dav4android.property.GetContentType;
import at.bitfire.dav4android.property.GetETag;
import at.bitfire.dav4android.property.ResourceType;
import lombok.Cleanup;
import lombok.NonNull;

public class DavResource {

    public final MediaType MIME_XML = MediaType.parse("application/xml; charset=utf-8");

    protected final OkHttpClient httpClient;
    protected static final int MAX_REDIRECTS = 5;

    protected final Logger log;

    public HttpUrl location;
    public final Set<String> capabilities = new HashSet<>();
    public final PropertyCollection properties = new PropertyCollection();
    public final Set<DavResource> members = new HashSet<>();

    static private PropertyRegistry registry = PropertyRegistry.DEFAULT;

    /**
     * Creates a new DavResource which represents a WebDAV resource at the given location.
     * @param log           #{@link Logger} which will be used for logging, or null for default
     * @param httpClient    #{@link OkHttpClient} to access this object
     * @param location      location of the WebDAV resource
     */
    public DavResource(Logger log, OkHttpClient httpClient, HttpUrl location) {
        this.log = log != null ? log : Constants.log;
        this.httpClient = httpClient;
        this.location = location;

        // Don't follow redirects (only useful for GET/POST).
        // This means we have to handle 30x responses manually.
        httpClient.setFollowRedirects(false);
    }


    public String fileName() {
        List<String> pathSegments = location.pathSegments();
        return pathSegments.get(pathSegments.size() - 1);
    }

    @Override
    public String toString() {
        return location.toString();
    }


    public void options() throws IOException, HttpException, DavException {
        capabilities.clear();

        Response response = httpClient.newCall(new Request.Builder()
                .method("OPTIONS", null)
                .url(location)
                .build()).execute();
        checkStatus(response);

        String dav = response.header("DAV");
        if (dav != null)
            for (String capability : TextUtils.split(dav, ","))
                capabilities.add(capability.trim());
    }


    /**
     * Sends a GET request to the resource. Note that this method expects the server to
     * return an ETag (which is required for CalDAV and CardDAV, but not for WebDAV in general).
     * @param accept    content of Accept header (must not be null, but may be &#42;&#47;* )
     * @return          response body
     * @throws DavException    on WebDAV errors, or when the response doesn't contain an ETag
     */
    public ResponseBody get(@NonNull String accept) throws IOException, HttpException, DavException {
        Response response = null;
        for (int attempt = 0; attempt < MAX_REDIRECTS; attempt++) {
            response = httpClient.newCall(new Request.Builder()
                    .get()
                    .url(location)
                    .header("Accept", accept)
                    .build()).execute();
            if (response.isRedirect())
                processRedirection(response);
            else
                break;
        }
        checkStatus(response);

        String eTag = response.header("ETag");
        if (TextUtils.isEmpty(eTag))
            // CalDAV servers MUST return ETag on GET [https://tools.ietf.org/html/rfc4791#section-5.3.4]
            // CardDAV servers MUST return ETag on GET [https://tools.ietf.org/html/rfc6352#section-6.3.2.3]
            throw new DavException("Received GET response without ETag");
        properties.put(GetETag.NAME, new GetETag(eTag));

        ResponseBody body = response.body();
        if (body == null)
            throw new HttpException("GET without response body");

        MediaType mimeType = body.contentType();
        if (mimeType != null)
            properties.put(GetContentType.NAME, new GetContentType(mimeType));

        return body;
    }

    /**
     * Sends a PUT request to the resource.
     * @param body              new resource body to upload
     * @param ifMatchETag       value of "If-Match" header to set, or null to omit
     * @param ifNoneMatch       indicates whether "If-None-Match: *" ("don't overwrite anything existing") header shall be sent
     * @return                  true if the request was redirected successfully, i.e. #{@link #location} and maybe resource name may have changed
     */
    public boolean put(@NonNull RequestBody body, String ifMatchETag, boolean ifNoneMatch) throws IOException, HttpException {
        boolean redirected = false;
        Response response = null;
        for (int attempt = 0; attempt < MAX_REDIRECTS; attempt++) {
            Request.Builder builder = new Request.Builder()
                    .put(body)
                    .url(location);

            if (ifMatchETag != null)
                // only overwrite specific version
                builder.header("If-Match", StringUtils.asQuotedString(ifMatchETag));
            if (ifNoneMatch)
                // don't overwrite anything existing
                builder.header("If-None-Match", "*");

            response = httpClient.newCall(builder.build()).execute();
            if (response.isRedirect()) {
                processRedirection(response);
                redirected = true;
            } else
                break;
        }
        checkStatus(response);

        String eTag = response.header("ETag");
        if (TextUtils.isEmpty(eTag))
            properties.remove(GetETag.NAME);
        else
            properties.put(GetETag.NAME, new GetETag(eTag));

        return redirected;
    }

    /**
     * Sends a DELETE request to the resource.
     * @param ifMatchETag       value of "If-Match" header to set, or null to omit
     * @throws HttpException    on HTTP errors, including redirections
     */
    public void delete(String ifMatchETag) throws IOException, HttpException {
        Request.Builder builder = new Request.Builder()
                .delete()
                .url(location);
        if (ifMatchETag != null)
            builder.header("If-Match", StringUtils.asQuotedString(ifMatchETag));
        Response response = httpClient.newCall(builder.build()).execute();
        checkStatus(response);
    }

    /**
     * Sends a PROPFIND request to the resource. Expects and processes a 207 multi-status response.
     * #{@link #properties} are updated according to the multi-status response.
     * #{@link #members} is re-built according to the multi-status response (i.e. previous member entries won't be retained).
     * @param depth      "Depth" header to send, e.g. 0 or 1
     * @param reqProp    properties to request
     */
    public void propfind(int depth, Property.Name... reqProp) throws IOException, HttpException, DavException {
        // build XML request body
        XmlSerializer serializer = XmlUtils.newSerializer();
        StringWriter writer = new StringWriter();
        serializer.setOutput(writer);
        serializer.setPrefix("", XmlUtils.NS_WEBDAV);
        serializer.setPrefix("CAL", XmlUtils.NS_CALDAV);
        serializer.setPrefix("CARD", XmlUtils.NS_CARDDAV);
        serializer.startDocument("UTF-8", null);
        serializer.setPrefix("", XmlUtils.NS_WEBDAV);
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
        for (int attempt = 0; attempt < MAX_REDIRECTS; attempt++) {
            response = httpClient.newCall(new Request.Builder()
                    .url(location)
                    .method("PROPFIND", RequestBody.create(MIME_XML, writer.toString()))
                    .header("Depth", String.valueOf(depth))
                    .build()).execute();
            if (response.isRedirect())
                processRedirection(response);
            else
                break;
        }

        checkStatus(response);
        assertMultiStatus(response);

        if (depth > 0)
            // collection listing requested, drop old member information
            members.clear();

        @Cleanup Reader reader = response.body().charStream();
        processMultiStatus(reader);
    }


    // status handling

    protected void checkStatus(int code, String message, Response response) throws HttpException {
        if (code/100 == 2)
            // everything OK
            return;

        switch (code) {
            case HttpURLConnection.HTTP_UNAUTHORIZED:
                throw response != null ? new UnauthorizedException(response) : new UnauthorizedException(message);
            case HttpURLConnection.HTTP_NOT_FOUND:
                throw response != null ? new NotFoundException(response) : new NotFoundException(message);
            case HttpURLConnection.HTTP_CONFLICT:
                throw response != null ? new ConflictException(response) : new ConflictException(message);
            case HttpURLConnection.HTTP_PRECON_FAILED:
                throw response != null ? new PreconditionFailedException(response) : new PreconditionFailedException(message);
            case HttpURLConnection.HTTP_UNAVAILABLE:
                throw response != null ? new ServiceUnavailableException(response) : new ServiceUnavailableException(message);
            default:
                throw response != null ? new HttpException(response) : new HttpException(code, message);
        }
    }

    protected void checkStatus(Response response) throws HttpException {
        checkStatus(response.code(), response.message(), response);
    }

    protected void checkStatus(StatusLine status) throws HttpException {
        checkStatus(status.code, status.message, null);
    }

    protected void assertMultiStatus(Response response) throws DavException {
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
            log.warn("Received multi-status response without Content-Type, assuming XML");
    }

    void processRedirection(Response response) throws HttpException {
        HttpUrl target = null;

        String href = response.header("Location");
        if (href != null)
            target = location.resolve(href);

        if (target != null) {
            log.debug("Received redirection, new location=" + target);
            location = target;
        } else
            throw new HttpException("Received redirection without new location");
    }


    // multi-status handling

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
                                log.warn("Invalid status line, treating as 500 Server Error");
                                status = new StatusLine(Protocol.HTTP_1_1, 500, "Invalid status line");
                            }
                            break;
                        case "propstat":
                            PropertyCollection prop = parseMultiStatus_PropStat(parser);
                            if (prop != null)
                                properties.merge(prop, false);
                            break;
                        case "location":
                            throw new UnsupportedDavException("Redirected child resources are not supported yet");
                    }
            }
            eventType = parser.next();
        }

        if (href == null) {
            log.warn("Ignoring <response> without valid <href>");
            return;
        }

        // if we know this resource is a collection, make sure href has a trailing slash (for clarity and resolving relative paths)
        ResourceType type = (ResourceType)properties.get(ResourceType.NAME);
        if (type != null && type.types.contains(ResourceType.COLLECTION))
            href = UrlUtils.withTrailingSlash(href);

        log.debug("Received <response> for " + href + ", status: " + status + ", properties: " + properties);

        if (status != null)
            // treat an HTTP error of a single response (i.e. requested resource or a member) like an HTTP error of the requested resource
            checkStatus(status);

        // Which resource does this <response> represent?
        DavResource target = null;
        if (UrlUtils.equals(UrlUtils.omitTrailingSlash(href), UrlUtils.omitTrailingSlash(location))) {
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
                    members.add(target = new DavResource(log, httpClient, href));
            }
        }

        // set properties for target
        if (target != null)
            target.properties.merge(properties, true);
        else
            log.warn("Received <response> not for self and not for member resource");
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
                                log.warn("Invalid status line, treating as 500 Server Error");
                                status = new StatusLine(Protocol.HTTP_1_1, 500, "Invalid status line");
                            }
                    }
            }
            eventType = parser.next();
        }

        if (status != null && status.code/100 != 2)
            // not successful, null out property values so that they can be removed when merging in parseMultiStatus_Response
            prop.nullAllValues();

        return prop;
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
                    log.debug("Ignoring unknown/unparseable property " + name);
            }
            eventType = parser.next();
        }

        return prop;
    }
}
