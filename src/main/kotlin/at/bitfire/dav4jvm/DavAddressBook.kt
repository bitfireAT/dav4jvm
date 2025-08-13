/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

package at.bitfire.dav4jvm

import at.bitfire.dav4jvm.XmlUtils.insertTag
import at.bitfire.dav4jvm.exception.DavException
import at.bitfire.dav4jvm.exception.HttpException
import at.bitfire.dav4jvm.property.carddav.AddressData
import at.bitfire.dav4jvm.property.carddav.NS_CARDDAV
import at.bitfire.dav4jvm.property.webdav.GetContentType
import at.bitfire.dav4jvm.property.webdav.GetETag
import at.bitfire.dav4jvm.property.webdav.NS_WEBDAV
import io.ktor.client.HttpClient
import io.ktor.client.request.prepareRequest
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.Url
import io.ktor.util.logging.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.StringWriter

@Suppress("unused")
class DavAddressBook @JvmOverloads constructor(
    httpClient: HttpClient,
    location: Url,
    logger: Logger = LoggerFactory.getLogger(DavAddressBook::javaClass.name)
): DavCollection(httpClient, location, logger) {

    companion object {
        val MIME_JCARD = ContentType.parse("application/vcard+json")
        val MIME_VCARD3_UTF8 = ContentType.parse("text/vcard;charset=utf-8")
        val MIME_VCARD4 = ContentType.parse("text/vcard;version=4.0")

        val ADDRESSBOOK_QUERY = Property.Name(NS_CARDDAV, "addressbook-query")
        val ADDRESSBOOK_MULTIGET = Property.Name(NS_CARDDAV, "addressbook-multiget")
        val FILTER = Property.Name(NS_CARDDAV, "filter")
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
    suspend fun addressbookQuery(callback: MultiResponseCallback): List<Property> {
        /* <!ELEMENT addressbook-query ((DAV:allprop |
                                         DAV:propname |
                                         DAV:prop)?, filter, limit?)>
           <!ELEMENT filter (prop-filter*)>
        */
        val serializer = XmlUtils.newSerializer()
        val writer = StringWriter()
        serializer.setOutput(writer)
        serializer.startDocument("UTF-8", null)
        serializer.setPrefix("", NS_WEBDAV)
        serializer.setPrefix("CARD", NS_CARDDAV)
        serializer.insertTag(ADDRESSBOOK_QUERY) {
            insertTag(PROP) {
                insertTag(GetETag.NAME)
            }
            insertTag(FILTER)
        }
        serializer.endDocument()

        followRedirects {
            httpClient.prepareRequest {
                url(location)
                method = HttpMethod.parse("REPORT")
                headers.append(HttpHeaders.ContentType, MIME_XML.toString())
                setBody(writer.toString())
                headers.append(HttpHeaders.Depth, "1")
            }.execute()
        }.let { response ->
            return processMultiStatus(response, callback)
        }
    }

    /**
     * Sends an addressbook-multiget REPORT request to the resource.
     *
     * @param urls         list of vCard URLs to be requested
     * @param contentType  MIME type of requested format; may be "text/vcard" for vCard or
     *                     "application/vcard+json" for jCard. *null*: don't request specific representation type
     * @param version      vCard version subtype of the requested format. Should only be specified together with a [contentType] of "text/vcard".
     *                     Currently only useful value: "4.0" for vCard 4. *null*: don't request specific version
     * @param callback     called for every WebDAV response XML element in the result
     *
     * @return list of properties which have been received in the Multi-Status response, but
     * are not part of response XML elements
     *
     * @throws IOException on I/O error
     * @throws HttpException on HTTP error
     * @throws DavException on WebDAV error
     */
    suspend fun multiget(urls: List<Url>, contentType: String? = null, version: String? = null, callback: MultiResponseCallback): List<Property> {
        /* <!ELEMENT addressbook-multiget ((DAV:allprop |
                                            DAV:propname |
                                            DAV:prop)?,
                                            DAV:href+)>
        */
        val serializer = XmlUtils.newSerializer()
        val writer = StringWriter()
        serializer.setOutput(writer)
        serializer.startDocument("UTF-8", null)
        serializer.setPrefix("", NS_WEBDAV)
        serializer.setPrefix("CARD", NS_CARDDAV)
        serializer.insertTag(ADDRESSBOOK_MULTIGET) {
            insertTag(PROP) {
                insertTag(GetContentType.NAME)
                insertTag(GetETag.NAME)
                insertTag(AddressData.NAME) {
                    if (contentType != null)
                        attribute(null, AddressData.CONTENT_TYPE, contentType)
                    if (version != null)
                        attribute(null, AddressData.VERSION, version)
                }
            }
            for (url in urls)
                insertTag(HREF) {
                    text(url.encodedPath)
                }
        }
        serializer.endDocument()

        followRedirects {
            httpClient.prepareRequest {
                url(location)
                method = HttpMethod.parse("REPORT")
                setBody(writer.toString())
                headers.append(HttpHeaders.ContentType, MIME_XML.toString())
                headers.append(HttpHeaders.Depth, "0")
            }.execute()
        }.let { response ->
            return processMultiStatus(response, callback)
        }
    }

}
