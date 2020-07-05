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
import okhttp3.*
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.io.StringWriter
import java.util.logging.Logger

/**
 * Represents a WebDAV collection.
 */
open class DavCollection @JvmOverloads constructor(
        httpClient: OkHttpClient,
        location: HttpUrl,
        log: Logger = Dav4jvm.log
): DavResource(httpClient, location, log) {

    companion object {
        val SYNC_COLLECTION = Property.Name(XmlUtils.NS_WEBDAV, "sync-collection")
        val SYNC_LEVEL = Property.Name(XmlUtils.NS_WEBDAV, "sync-level")
        val LIMIT = Property.Name(XmlUtils.NS_WEBDAV, "limit")
        val NRESULTS = Property.Name(XmlUtils.NS_WEBDAV, "nresults")
    }

    /**
     * Sends a POST request. Primarily intended to be used with an Add-Member URL (RFC 5995).
     */
    @Throws(IOException::class, HttpException::class)
    fun post(body: RequestBody, ifNoneMatch: Boolean = false, callback: (Response) -> Unit) {
        followRedirects {
            val builder = Request.Builder()
                    .post(body)
                    .url(location)

            if (ifNoneMatch)
                // don't overwrite anything existing
                builder.header("If-None-Match", "*")

            httpClient.newCall(builder.build()).execute()
        }.use { response ->
            checkStatus(response)
            callback(response)
        }
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
    fun reportChanges(syncToken: String?, infiniteDepth: Boolean, limit: Int?, vararg properties: Property.Name, callback: DavResponseCallback): List<Property> {
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