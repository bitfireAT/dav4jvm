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

public class DavAddressBook extends DavResource {

    public static final MediaType
            MIME_VCARD3_UTF8 = MediaType.parse("text/vcard;charset=utf-8"),
            MIME_VCARD4 = MediaType.parse("text/vcard;version=4.0");

    public DavAddressBook(Logger log, OkHttpClient httpClient, HttpUrl location) {
        super(log, httpClient, location);
    }

    public void addressbookQuery() throws IOException, HttpException, DavException {
        /* <!ELEMENT addressbook-query ((DAV:allprop |
                                         DAV:propname |
                                         DAV:prop)?, filter, limit?)>
           <!ELEMENT filter (prop-filter*)>
        */
        XmlSerializer serializer = XmlUtils.newSerializer();
        StringWriter writer = new StringWriter();
        serializer.setOutput(writer);
        serializer.startDocument("UTF-8", null);
        serializer.setPrefix("", XmlUtils.NS_WEBDAV);
        serializer.setPrefix("CARD", XmlUtils.NS_CARDDAV);
        serializer.startTag(XmlUtils.NS_CARDDAV, "addressbook-query");
            serializer.startTag(XmlUtils.NS_WEBDAV, "prop");
                serializer.startTag(XmlUtils.NS_WEBDAV, "getetag");
                serializer.endTag(XmlUtils.NS_WEBDAV, "getetag");
            serializer.endTag(XmlUtils.NS_WEBDAV, "prop");
            serializer.startTag(XmlUtils.NS_CARDDAV, "filter");
            serializer.endTag(XmlUtils.NS_CARDDAV,   "filter");
        serializer.endTag(XmlUtils.NS_CARDDAV, "addressbook-query");
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

    public void multiget(HttpUrl[] urls, boolean vCard4) throws IOException, HttpException, DavException {
        /* <!ELEMENT addressbook-multiget ((DAV:allprop |
                                            DAV:propname |
                                            DAV:prop)?,
                                            DAV:href+)>
        */
        XmlSerializer serializer = XmlUtils.newSerializer();
        StringWriter writer = new StringWriter();
        serializer.setOutput(writer);
        serializer.startDocument("UTF-8", null);
        serializer.setPrefix("", XmlUtils.NS_WEBDAV);
        serializer.setPrefix("CARD", XmlUtils.NS_CARDDAV);
        serializer.startTag(XmlUtils.NS_CARDDAV, "addressbook-multiget");
        serializer.startTag(XmlUtils.NS_WEBDAV, "prop");
        serializer.startTag(XmlUtils.NS_WEBDAV, "getcontenttype");      // to determine the character set
        serializer.endTag(XmlUtils.NS_WEBDAV, "getcontenttype");
        serializer.startTag(XmlUtils.NS_WEBDAV, "getetag");
        serializer.endTag(XmlUtils.NS_WEBDAV, "getetag");
        serializer.startTag(XmlUtils.NS_CARDDAV, "address-data");
        if (vCard4) {
            serializer.attribute(null, "content-type", "text/vcard");
            serializer.attribute(null, "version", "4.0");
        }
        serializer.endTag(XmlUtils.NS_CARDDAV, "address-data");
        serializer.endTag(XmlUtils.NS_WEBDAV, "prop");
        for (HttpUrl url : urls) {
            serializer.startTag(XmlUtils.NS_WEBDAV, "href");
            serializer.text(url.encodedPath());
            serializer.endTag(XmlUtils.NS_WEBDAV, "href");
        }
        serializer.endTag(XmlUtils.NS_CARDDAV, "addressbook-multiget");
        serializer.endDocument();

        // redirects must not followed automatically (as it may rewrite REPORT requests to GET requests)
        httpClient.setFollowRedirects(false);

        Response response = httpClient.newCall(new Request.Builder()
                .url(location)
                .method("REPORT", RequestBody.create(MIME_XML, writer.toString()))
                .header("Depth", "0")       // "The request MUST include a Depth: 0 header [...]"
                .build()).execute();

        checkStatus(response);
        assertMultiStatus(response);

        members.clear();
        processMultiStatus(response.body().charStream());
    }

}
