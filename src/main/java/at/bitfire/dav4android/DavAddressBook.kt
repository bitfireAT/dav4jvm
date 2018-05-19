/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.dav4android

import at.bitfire.dav4android.exception.DavException
import at.bitfire.dav4android.exception.HttpException
import okhttp3.*
import java.io.IOException
import java.io.StringWriter
import java.util.logging.Logger

class DavAddressBook @JvmOverloads constructor(
        httpClient: OkHttpClient,
        location: HttpUrl,
        log: Logger = Constants.log
): DavCollection(httpClient, location, log) {

    companion object {
        val MIME_VCARD3_UTF8 = MediaType.parse("text/vcard;charset=utf-8")
        val MIME_VCARD4 = MediaType.parse("text/vcard;version=4.0")
    }


    /**
     * Sends an addressbook-query REPORT request to the resource.
     * @throws IOException on I/O error
     * @throws HttpException on HTTP error
     * @throws DavException on DAV error
     */
    fun addressbookQuery(): DavResponse {
        /* <!ELEMENT addressbook-query ((DAV:allprop |
                                         DAV:propname |
                                         DAV:prop)?, filter, limit?)>
           <!ELEMENT filter (prop-filter*)>
        */
        val serializer = XmlUtils.newSerializer()
        val writer = StringWriter()
        serializer.setOutput(writer)
        serializer.startDocument("UTF-8", null)
        serializer.setPrefix("", XmlUtils.NS_WEBDAV)
        serializer.setPrefix("CARD", XmlUtils.NS_CARDDAV)
        serializer.startTag(XmlUtils.NS_CARDDAV, "addressbook-query")
            serializer.startTag(XmlUtils.NS_WEBDAV, "prop")
                serializer.startTag(XmlUtils.NS_WEBDAV, "getetag")
                serializer.endTag(XmlUtils.NS_WEBDAV, "getetag")
            serializer.endTag(XmlUtils.NS_WEBDAV, "prop")
            serializer.startTag(XmlUtils.NS_CARDDAV, "filter")
            serializer.endTag(XmlUtils.NS_CARDDAV,   "filter")
        serializer.endTag(XmlUtils.NS_CARDDAV, "addressbook-query")
        serializer.endDocument()

        val response = httpClient.newCall(Request.Builder()
                .url(location)
                .method("REPORT", RequestBody.create(MIME_XML, writer.toString()))
                .header("Depth", "1")
                .build()).execute()

        checkStatus(response)
        assertMultiStatus(response)

        return processMultiStatus(response.body()?.charStream()!!)
    }

    /**
     * Sends an addressbook-multiget REPORT request to the resource.
     * @throws IOException on I/O error
     * @throws HttpException on HTTP error
     * @throws DavException on DAV error
     */
    fun multiget(urls: List<HttpUrl>, vCard4: Boolean): DavResponse {
        /* <!ELEMENT addressbook-multiget ((DAV:allprop |
                                            DAV:propname |
                                            DAV:prop)?,
                                            DAV:href+)>
        */
        val serializer = XmlUtils.newSerializer()
        val writer = StringWriter()
        serializer.setOutput(writer)
        serializer.startDocument("UTF-8", null)
        serializer.setPrefix("", XmlUtils.NS_WEBDAV)
        serializer.setPrefix("CARD", XmlUtils.NS_CARDDAV)
        serializer.startTag(XmlUtils.NS_CARDDAV, "addressbook-multiget")
            serializer.startTag(XmlUtils.NS_WEBDAV, "prop")
                serializer.startTag(XmlUtils.NS_WEBDAV, "getcontenttype")      // to determine the character set
                serializer.endTag(XmlUtils.NS_WEBDAV, "getcontenttype")
                serializer.startTag(XmlUtils.NS_WEBDAV, "getetag")
                serializer.endTag(XmlUtils.NS_WEBDAV, "getetag")
                serializer.startTag(XmlUtils.NS_CARDDAV, "address-data")
                if (vCard4) {
                    serializer.attribute(null, "content-type", "text/vcard")
                    serializer.attribute(null, "version", "4.0")
                }
                serializer.endTag(XmlUtils.NS_CARDDAV, "address-data")
            serializer.endTag(XmlUtils.NS_WEBDAV, "prop")
            for (url in urls) {
                serializer.startTag(XmlUtils.NS_WEBDAV, "href")
                    serializer.text(url.encodedPath())
                serializer.endTag(XmlUtils.NS_WEBDAV, "href")
            }
        serializer.endTag(XmlUtils.NS_CARDDAV, "addressbook-multiget")
        serializer.endDocument()

        val response = httpClient.newCall(Request.Builder()
                .url(location)
                .method("REPORT", RequestBody.create(MIME_XML, writer.toString()))
                .header("Depth", "0")       // "The request MUST include a Depth: 0 header [...]"
                .build()).execute()

        checkStatus(response)
        assertMultiStatus(response)

        return processMultiStatus(response.body()?.charStream()!!)
    }

}
