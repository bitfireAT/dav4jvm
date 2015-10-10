/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.dav4android;

import com.squareup.okhttp.HttpUrl;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;

import org.xmlpull.v1.XmlSerializer;

import java.io.IOException;
import java.io.StringWriter;

import at.bitfire.dav4android.exception.DavException;
import at.bitfire.dav4android.exception.HttpException;

public class DavAddressBook extends DavResource {

    public DavAddressBook(OkHttpClient httpClient, HttpUrl location) {
        super(httpClient, location);
    }

    public void queryMemberETags() throws IOException, HttpException, DavException {
        // build XML request body
        XmlSerializer serializer = XmlUtils.newSerializer();
        StringWriter writer = new StringWriter();
        serializer.setOutput(writer);
        serializer.startDocument("UTF-8", null);
        serializer.startTag(XmlUtils.NS_CARDDAV, "addressbook-query");
        serializer.startTag(XmlUtils.NS_WEBDAV, "prop");
        serializer.startTag(XmlUtils.NS_WEBDAV, "getetag");
        serializer.endTag(XmlUtils.NS_WEBDAV, "getetag");
        serializer.endTag(XmlUtils.NS_WEBDAV, "prop");
        serializer.endTag(XmlUtils.NS_CARDDAV, "addressbook-query");
        serializer.endDocument();

        // redirects must not followed automatically (as it may rewrite REPORT requests to GET requests)
        httpClient.setFollowRedirects(false);

        Response response = httpClient.newCall(new Request.Builder()
                .url(location)
                .method("REPORT", RequestBody.create(MEDIA_TYPE_XML, writer.toString()))
                .header("Depth", "1")
                .build()).execute();

        checkStatus(response);
        assertMultiStatus(response);

        members.clear();
        processMultiStatus(response.body().charStream());
    }

}
