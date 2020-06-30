/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm

import at.bitfire.dav4jvm.XmlUtils.insertTag
import at.bitfire.dav4jvm.exception.DavException
import at.bitfire.dav4jvm.exception.HttpException
import at.bitfire.dav4jvm.property.AddressData
import at.bitfire.dav4jvm.property.GetContentType
import at.bitfire.dav4jvm.property.GetETag
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.io.StringWriter
import java.util.logging.Logger

class DavAddressBook @JvmOverloads constructor(
        httpClient: OkHttpClient,
        location: HttpUrl,
        log: Logger = Dav4jvm.log
): DavCollection(httpClient, location, log) {

    companion object {
        val MIME_VCARD3_UTF8 = "text/vcard;charset=utf-8".toMediaType()
        val MIME_VCARD4 = "text/vcard;version=4.0".toMediaType()

        val ADDRESSBOOK_QUERY = Property.Name(XmlUtils.NS_CARDDAV, "addressbook-query")
        val ADDRESSBOOK_MULTIGET = Property.Name(XmlUtils.NS_CARDDAV, "addressbook-multiget")
        val FILTER = Property.Name(XmlUtils.NS_CARDDAV, "filter")
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
        serializer.insertTag(ADDRESSBOOK_QUERY) {
            insertTag(PROP) {
                insertTag(GetETag.NAME)
            }
            insertTag(FILTER)
        }
        serializer.endDocument()

        followRedirects {
            httpClient.newCall(Request.Builder()
                    .url(location)
                    .method("REPORT", writer.toString().toRequestBody(MIME_XML))
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
        serializer.insertTag(ADDRESSBOOK_MULTIGET) {
            insertTag(PROP) {
                insertTag(GetContentType.NAME)
                insertTag(GetETag.NAME)
                insertTag(AddressData.NAME) {
                    if (vCard4) {
                        attribute(null, AddressData.CONTENT_TYPE, "text/vcard")
                        attribute(null, AddressData.VERSION, "4.0")
                    }
                }
            }
            for (url in urls)
                insertTag(HREF) {
                    text(url.encodedPath)
                }
        }
        serializer.endDocument()

        followRedirects {
            httpClient.newCall(Request.Builder()
                    .url(location)
                    .method("REPORT", writer.toString().toRequestBody(MIME_XML))
                    .header("Depth", "0")       // "The request MUST include a Depth: 0 header [...]"
                    .build()).execute()
        }.use {
            return processMultiStatus(it, callback)
        }
    }

}
