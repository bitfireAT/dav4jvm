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
import at.bitfire.dav4jvm.okhttp.exception.DavException
import at.bitfire.dav4jvm.okhttp.exception.HttpException
import at.bitfire.dav4jvm.property.webdav.SyncToken
import at.bitfire.dav4jvm.property.webdav.WebDAV
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.StringWriter
import java.util.logging.Logger

/**
 * Represents a WebDAV collection.
 */
open class DavCollection @JvmOverloads constructor(
    httpClient: OkHttpClient,
    location: HttpUrl,
    logger: Logger = Logger.getLogger(DavCollection::class.java.name)
): DavResource(httpClient, location, logger) {

    /**
     * Sends a REPORT sync-collection request.
     *
     * @param syncToken     sync-token to be sent with the request
     * @param infiniteDepth sync-level to be sent with the request: false = "1", true = "infinite"
     * @param limit         maximum number of results (may cause truncation)
     * @param properties    WebDAV properties to be requested
     * @param callback      called for every WebDAV response XML element in the result
     *
     * @return list of properties which have been received in the Multi-Status response, but
     * are not part of response XML elements (like `sync-token` which is returned as [SyncToken])
     *
     * @throws java.io.IOException on I/O error
     * @throws HttpException on HTTP error
     * @throws DavException on WebDAV error
     */
    fun reportChanges(syncToken: String?, infiniteDepth: Boolean, limit: Int?, vararg properties: Property.Name, callback: MultiResponseCallback): List<Property> {
        /* <!ELEMENT sync-collection (sync-token, sync-level, limit?, prop)>

           <!ELEMENT sync-token CDATA>       <!-- Text MUST be a URI -->
           <!ELEMENT sync-level CDATA>       <!-- Text MUST be either "1" or "infinite" -->

           <!ELEMENT limit (nresults) >
           <!ELEMENT nresults (#PCDATA)> <!-- only digits -->

           <!-- DAV:prop defined in RFC 4918, Section 14.18 -->
        */
        val serializer = XmlUtils.newSerializer()
        val writer = StringWriter()
        serializer.setOutput(writer)
        serializer.startDocument("UTF-8", null)
        serializer.setPrefix("", WebDAV.NS_WEBDAV)
        serializer.insertTag(WebDAV.SyncCollection) {
            insertTag(WebDAV.SyncToken) {
                if (syncToken != null)
                    text(syncToken)
            }
            insertTag(WebDAV.SyncLevel) {
                text(if (infiniteDepth) "infinite" else "1")
            }
            if (limit != null)
                insertTag(WebDAV.Limit) {
                    insertTag(WebDAV.NResults) {
                        text(limit.toString())
                    }
                }
            insertTag(WebDAV.Prop) {
                for (prop in properties)
                    insertTag(prop)
            }
        }
        serializer.endDocument()

        followRedirects {
            httpClient.newCall(Request.Builder()
                    .url(location)
                    .method("REPORT", writer.toString().toRequestBody(MIME_XML))
                    .header("Depth", "0")
                    .build()).execute()
        }.use {
            return processMultiStatus(it, callback)
        }
    }

}