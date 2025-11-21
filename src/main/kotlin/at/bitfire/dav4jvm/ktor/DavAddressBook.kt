/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

package at.bitfire.dav4jvm.ktor

import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.XmlUtils
import at.bitfire.dav4jvm.XmlUtils.insertTag
import at.bitfire.dav4jvm.property.carddav.AddressData
import at.bitfire.dav4jvm.property.carddav.CardDAV
import at.bitfire.dav4jvm.property.webdav.WebDAV
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.prepareRequest
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.util.logging.Logger
import org.slf4j.LoggerFactory
import java.io.StringWriter

class DavAddressBook(
    httpClient: HttpClient,
    location: Url,
    logger: Logger = LoggerFactory.getLogger(DavAddressBook::javaClass.name)
): DavCollection(httpClient, location, logger) {

    /**
     * Sends an addressbook-query REPORT request to the resource.
     *
     * @param callback called for every WebDAV response XML element in the result
     *
     * @return list of properties which have been received in the Multi-Status response, but
     * are not part of response XML elements
     *
     * @throws java.io.IOException on I/O error
     * @throws at.bitfire.dav4jvm.ktor.exception.HttpException on HTTP error
     * @throws at.bitfire.dav4jvm.ktor.exception.DavException on WebDAV error
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
        serializer.setPrefix("", WebDAV.NS_WEBDAV)
        serializer.setPrefix("CARD", CardDAV.NS_CARDDAV)
        serializer.insertTag(CardDAV.AddressbookQuery) {
            insertTag(WebDAV.Prop) {
                insertTag(WebDAV.GetETag)
            }
            insertTag(CardDAV.Filter)
        }
        serializer.endDocument()

        var result: List<Property>? = null
        followRedirects({
            httpClient.prepareRequest(location) {
                method = HttpMethod.parse("REPORT")

                header(HttpHeaders.Depth, "1")

                contentType(MIME_XML_UTF8)
                setBody(writer.toString())
            }
        }) { response ->
            result = processMultiStatus(response, callback)
        }
        return result ?: emptyList()
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
     * @throws java.io.IOException on I/O error
     * @throws at.bitfire.dav4jvm.ktor.exception.HttpException on HTTP error
     * @throws at.bitfire.dav4jvm.ktor.exception.DavException on WebDAV error
     */
    suspend fun multiget(
        urls: List<Url>,
        contentType: String? = null,
        version: String? = null,
        callback: MultiResponseCallback
    ): List<Property> {
        /* <!ELEMENT addressbook-multiget ((DAV:allprop |
                                            DAV:propname |
                                            DAV:prop)?,
                                            DAV:href+)>
        */
        val serializer = XmlUtils.newSerializer()
        val writer = StringWriter()
        serializer.setOutput(writer)
        serializer.startDocument("UTF-8", null)
        serializer.setPrefix("", WebDAV.NS_WEBDAV)
        serializer.setPrefix("CARD", CardDAV.NS_CARDDAV)
        serializer.insertTag(CardDAV.AddressbookMultiget) {
            insertTag(WebDAV.Prop) {
                insertTag(WebDAV.GetContentType)
                insertTag(WebDAV.GetETag)
                insertTag(CardDAV.AddressData) {
                    if (contentType != null)
                        attribute(null, AddressData.CONTENT_TYPE, contentType)
                    if (version != null)
                        attribute(null, AddressData.VERSION, version)
                }
            }
            for (url in urls)
                insertTag(WebDAV.Href) {
                    text(url.encodedPath)
                }
        }
        serializer.endDocument()

        var result: List<Property>? = null
        followRedirects({
            httpClient.prepareRequest(location) {
                method = HttpMethod.parse("REPORT")

                header(HttpHeaders.Depth, "0")

                contentType(MIME_XML_UTF8)
                setBody(writer.toString())
            }
        }) { response ->
            result = processMultiStatus(response, callback)
        }
        return result ?: emptyList()
    }


    companion object {

        val MIME_JCARD = ContentType.parse("application/vcard+json")
        val MIME_VCARD3_UTF8 = ContentType.parse("text/vcard;charset=utf-8")
        val MIME_VCARD4 = ContentType.parse("text/vcard;version=4.0")

    }

}