/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

package at.bitfire.dav4jvm.okhttp

import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.XmlUtils
import at.bitfire.dav4jvm.XmlUtils.insertTag
import at.bitfire.dav4jvm.property.carddav.AddressData
import at.bitfire.dav4jvm.property.carddav.CardDAV
import at.bitfire.dav4jvm.property.webdav.WebDAV
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.StringWriter
import java.util.logging.Logger

@Suppress("unused")
class DavAddressBook @JvmOverloads constructor(
    httpClient: OkHttpClient,
    location: HttpUrl,
    logger: Logger = Logger.getLogger(DavAddressBook::javaClass.name)
): DavCollection(httpClient, location, logger) {

    companion object {
        val MIME_JCARD = "application/vcard+json".toMediaType()
        val MIME_VCARD3_UTF8 = "text/vcard;charset=utf-8".toMediaType()
        val MIME_VCARD4 = "text/vcard;version=4.0".toMediaType()
    }

    /**
     * Sends an addressbook-query REPORT request to the resource.
     *
     * @param callback called for every WebDAV response XML element in the result
     *
     * @return list of properties which have been received in the Multi-Status response, but
     * are not part of response XML elements
     *
     * @throws java.io.IOException on I/O error
     * @throws at.bitfire.dav4jvm.okhttp.exception.HttpException on HTTP error
     * @throws at.bitfire.dav4jvm.okhttp.exception.DavException on WebDAV error
     */
    fun addressbookQuery(callback: MultiResponseCallback): List<Property> {
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

        followRedirects {
            httpClient.newCall(
                Request.Builder()
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
     * @throws at.bitfire.dav4jvm.okhttp.exception.HttpException on HTTP error
     * @throws at.bitfire.dav4jvm.okhttp.exception.DavException on WebDAV error
     */
    fun multiget(urls: List<HttpUrl>, contentType: String? = null, version: String? = null, callback: MultiResponseCallback): List<Property> {
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

        followRedirects {
            httpClient.newCall(
                Request.Builder()
                    .url(location)
                    .method("REPORT", writer.toString().toRequestBody(MIME_XML))
                    .header("Depth", "0")       // "The request MUST include a Depth: 0 header [...]"
                    .build()).execute()
        }.use {
            return processMultiStatus(it, callback)
        }
    }

}