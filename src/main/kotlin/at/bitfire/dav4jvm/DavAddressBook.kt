/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm

import at.bitfire.dav4jvm.exception.DavException
import at.bitfire.dav4jvm.exception.HttpException
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
     *
     * @param callback called for every WebDAV response XML element in the result
     *
     * @return list of properties which have been received in the Multi-Status response, but
     * are not part of response XML elements
     *
     * @throws IOException on I/O error
     * @throws HttpException on HTTP error
     * @throws DavException on WebDAV error
     */
    fun addressbookQuery(callback: DavResponseCallback): List<Property> {
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

        followRedirects {
            httpClient.newCall(Request.Builder()
                    .url(location)
                    .method("REPORT", RequestBody.create(MIME_XML, writer.toString()))
                    .header("Depth", "1")
                    .build()).execute()
        }.use { response ->
            return processMultiStatus(response, callback)
        }
    }

    /**
     * Sends an addressbook-multiget REPORT request to the resource.
     *
     * @param urls     list of vCard URLs to be requested
     * @param vCard4   whether vCards should be requested as vCard4 4.0 (true: 4.0, false: 3.0)
     * @param callback called for every WebDAV response XML element in the result
     *
     * @return list of properties which have been received in the Multi-Status response, but
     * are not part of response XML elements
     *
     * @throws IOException on I/O error
     * @throws HttpException on HTTP error
     * @throws DavException on WebDAV error
     */
    fun multiget(urls: List<HttpUrl>, vCard4: Boolean, callback: DavResponseCallback): List<Property> {
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

        followRedirects {
            httpClient.newCall(Request.Builder()
                    .url(location)
                    .method("REPORT", RequestBody.create(MIME_XML, writer.toString()))
                    .header("Depth", "0")       // "The request MUST include a Depth: 0 header [...]"
                    .build()).execute()
        }.use {
            return processMultiStatus(it, callback)
        }
    }

}
