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

import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmlpull.v1.XmlSerializer;

import java.io.IOException;
import java.io.StringWriter;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class DavResource {

    final public MediaType MEDIA_TYPE_XML = MediaType.parse("text/xml; charset=UTF-8");

    final protected OkHttpClient httpClient;

    final HttpUrl location;
    final PropertyCollection properties = new PropertyCollection();


    public void propfind(Property.Name... reqProp) throws XmlPullParserException, IOException, HttpException {
        // build XML request body
        XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
        XmlSerializer serializer = factory.newSerializer();
        StringWriter writer = new StringWriter();
        serializer.setOutput(writer);
        serializer.startDocument("UTF-8", false);
        serializer.startTag(Property.NS_WEBDAV, "propfind");
        serializer.startTag(Property.NS_WEBDAV, "prop");
        for (Property.Name prop : reqProp) {
            serializer.startTag(prop.namespace, prop.name);
            serializer.endTag(prop.namespace, prop.name);
        }
        serializer.endTag(Property.NS_WEBDAV, "prop");
        serializer.endTag(Property.NS_WEBDAV, "propfind");
        serializer.endDocument();

        Response response = httpClient.newCall(new Request.Builder()
                .url(location)
                .method("PROPFIND", RequestBody.create(MEDIA_TYPE_XML, writer.toString()))
                .build()).execute();

        checkResponse(response);

        // TODO process body
        // response.body().byteStream()
    }


    protected void checkResponse(Response response) throws HttpException {
        int status = response.code();

        if (status/100 == 1 || status/100 == 2)
            // everything OK
            return;

        throw new HttpException(status, response.message());
    }

}
