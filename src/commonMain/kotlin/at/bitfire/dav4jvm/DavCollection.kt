/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm

import at.bitfire.dav4jvm.XmlUtils.insertTag
import at.bitfire.dav4jvm.exception.DavException
import at.bitfire.dav4jvm.exception.HttpException
import at.bitfire.dav4jvm.property.SyncToken
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.util.logging.*
import io.ktor.utils.io.errors.*
import nl.adaptivity.xmlutil.QName
import kotlin.coroutines.cancellation.CancellationException
import kotlin.jvm.JvmOverloads

/**
 * Represents a WebDAV collection.
 */
open class DavCollection @JvmOverloads constructor(
    httpClient: HttpClient,
    location: Url,
    log: Logger = Dav4jvm.log
) : DavResource(httpClient, location, log) {

    companion object {
        val SYNC_COLLECTION = QName(XmlUtils.NS_WEBDAV, "sync-collection")
        val SYNC_LEVEL = QName(XmlUtils.NS_WEBDAV, "sync-level")
        val LIMIT = QName(XmlUtils.NS_WEBDAV, "limit")
        val NRESULTS = QName(XmlUtils.NS_WEBDAV, "nresults")
    }

    /**
     * Sends a POST request. Primarily intended to be used with an Add-Member URL (RFC 5995).
     */
    @Throws(IOException::class, HttpException::class, CancellationException::class)
    suspend fun post(body: Any, contentType: ContentType, ifNoneMatch: Boolean = false, callback: ResponseCallback) {
        //TODO followRedirects {
        val response = httpClient.prepareRequest {
            method = HttpMethod.Post
            setBody(body)
            url(location)

            if (ifNoneMatch)
            // don't overwrite anything existing
                header(HttpHeaders.IfNoneMatch, "*")
        }.execute()

        checkStatus(response)
        callback.onResponse(response)
    }

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
    suspend fun reportChanges(
        syncToken: String?,
        infiniteDepth: Boolean,
        limit: Int?,
        vararg properties: QName,
        callback: MultiResponseCallback
    ): List<Property> {
        /* <!ELEMENT sync-collection (sync-token, sync-level, limit?, prop)>

           <!ELEMENT sync-token CDATA>       <!-- Text MUST be a URI -->
           <!ELEMENT sync-level CDATA>       <!-- Text MUST be either "1" or "infinite" -->

           <!ELEMENT limit (nresults) >
           <!ELEMENT nresults (#PCDATA)> <!-- only digits -->

           <!-- DAV:prop defined in RFC 4918, Section 14.18 -->
        */
        val writer = StringBuilder()
        val serializer = XmlUtils.createWriter(writer)
        serializer.startDocument(encoding = "UTF-8")
        serializer.setPrefix("", XmlUtils.NS_WEBDAV)
        serializer.insertTag(SYNC_COLLECTION) {
            insertTag(SyncToken.NAME) {
                if (syncToken != null)
                    text(syncToken)
            }
            insertTag(SYNC_LEVEL) {
                text(if (infiniteDepth) "infinite" else "1")
            }
            if (limit != null)
                insertTag(LIMIT) {
                    insertTag(NRESULTS) {
                        text(limit.toString())
                    }
                }
            insertTag(PROP) {
                for (prop in properties)
                    insertTag(prop)
            }
        }
        serializer.endDocument()

        //TODO followRedirects {
        val response = httpClient.prepareRequest {
            url(location)
            method = Report
            setBody(writer.toString())
            header(HttpHeaders.ContentType, MIME_XML)
            header("Depth", "0")
        }.execute()
        return processMultiStatus(response, callback)
    }

}