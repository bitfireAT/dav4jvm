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
import at.bitfire.dav4jvm.QuotedStringUtils
import at.bitfire.dav4jvm.XmlReader
import at.bitfire.dav4jvm.XmlUtils
import at.bitfire.dav4jvm.XmlUtils.insertTag
import at.bitfire.dav4jvm.XmlUtils.propertyName
import at.bitfire.dav4jvm.okhttp.DavResource.Companion.MAX_REDIRECTS
import at.bitfire.dav4jvm.okhttp.exception.ConflictException
import at.bitfire.dav4jvm.okhttp.exception.DavException
import at.bitfire.dav4jvm.okhttp.exception.ForbiddenException
import at.bitfire.dav4jvm.okhttp.exception.GoneException
import at.bitfire.dav4jvm.okhttp.exception.HttpException
import at.bitfire.dav4jvm.okhttp.exception.NotFoundException
import at.bitfire.dav4jvm.okhttp.exception.PreconditionFailedException
import at.bitfire.dav4jvm.okhttp.exception.ServiceUnavailableException
import at.bitfire.dav4jvm.okhttp.exception.UnauthorizedException
import at.bitfire.dav4jvm.property.caldav.CalDAV
import at.bitfire.dav4jvm.property.carddav.CardDAV
import at.bitfire.dav4jvm.property.webdav.SyncToken
import at.bitfire.dav4jvm.property.webdav.WebDAV
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.EOFException
import java.io.IOException
import java.io.Reader
import java.io.StringWriter
import java.util.logging.Level
import java.util.logging.Logger
import at.bitfire.dav4jvm.okhttp.Response as DavResponse

/**
 * Represents a WebDAV resource at the given location and allows WebDAV
 * requests to be performed on this resource.
 *
 * Requests are executed synchronously (blocking). If no error occurs, the given
 * callback will be called. Otherwise, an exception is thrown. *These callbacks
 * don't need to close the response.*
 *
 * To cancel a request, interrupt the thread. This will cause the requests to
 * throw `InterruptedException` or `InterruptedIOException`.
 *
 * @param httpClient    [OkHttpClient] to access this object (must not follow redirects)
 * @param location      location of the WebDAV resource
 * @param logger        will be used for logging
 */
open class DavResource @JvmOverloads constructor(
    val httpClient: OkHttpClient,
    location: HttpUrl,
    val logger: Logger = Logger.getLogger(DavResource::class.java.name)
) {

    companion object {
        const val MAX_REDIRECTS = 5

        const val HTTP_MULTISTATUS = 207
        val MIME_XML = "application/xml; charset=utf-8".toMediaType()

        val XML_SIGNATURE = "<?xml".toByteArray()


        /**
         * Creates a request body for the PROPPATCH request.
         */
        internal fun createProppatchXml(
            setProperties: Map<Property.Name, String>,
            removeProperties: List<Property.Name>
        ): String {
            // build XML request body
            val serializer = XmlUtils.newSerializer()
            val writer = StringWriter()
            serializer.setOutput(writer)
            serializer.setPrefix("d", WebDAV.NS_WEBDAV)
            serializer.startDocument("UTF-8", null)
            serializer.insertTag(WebDAV.PropertyUpdate) {
                // DAV:set
                if (setProperties.isNotEmpty()) {
                    serializer.insertTag(WebDAV.Set) {
                        for (prop in setProperties) {
                            serializer.insertTag(WebDAV.Prop) {
                                serializer.insertTag(prop.key) {
                                    text(prop.value)
                                }
                            }
                        }
                    }
                }

                // DAV:remove
                if (removeProperties.isNotEmpty()) {
                    serializer.insertTag(WebDAV.Remove) {
                        for (prop in removeProperties) {
                            insertTag(WebDAV.Prop) {
                                insertTag(prop)
                            }
                        }
                    }
                }
            }

            serializer.endDocument()
            return writer.toString()
        }

    }

    /**
     * URL of this resource (changes when being redirected by server)
     */
    var location: HttpUrl
        private set             // allow internal modification only (for redirects)

    init {
        // Don't follow redirects (only useful for GET/POST).
        // This means we have to handle 30x responses ourselves.
        require(!httpClient.followRedirects) { "httpClient must not follow redirects automatically" }

        this.location = location
    }

    override fun toString() = location.toString()


    /**
     * Gets the file name of this resource. See [at.bitfire.dav4jvm.okhttp.OkHttpUtils.fileName] for details.
     */
    fun fileName() = OkHttpUtils.fileName(location)


    /**
     * Sends an OPTIONS request to this resource without HTTP compression (because some servers have
     * broken compression for OPTIONS). Follows up to [MAX_REDIRECTS] redirects when set.
     *
     * @param followRedirects whether redirects should be followed (default: *false*)
     * @param callback called with server response unless an exception is thrown
     *
     * @throws IOException on I/O error
     * @throws HttpException on HTTP error
     * @throws DavException on HTTPS -> HTTP redirect
     */
    @Throws(IOException::class, HttpException::class)
    fun options(followRedirects: Boolean = false, callback: CapabilitiesCallback) {
        val requestOptions = {
            httpClient.newCall(Request.Builder()
                .method("OPTIONS", null)
                .header("Content-Length", "0")
                .url(location)
                .header("Accept-Encoding", "identity")      // disable compression
                .build()).execute()
        }
        val response = if (followRedirects)
            followRedirects(requestOptions)
        else
            requestOptions()
        response.use {
            checkStatus(response)
            callback.onCapabilities(
                OkHttpUtils.listHeader(response, "DAV").map { it.trim() }.toSet(),
                response
            )
        }
    }

    /**
     * Sends a MOVE request to this resource. Follows up to [MAX_REDIRECTS] redirects.
     * Updates [location] on success.
     *
     * @param destination where the resource shall be moved to
     * @param overwrite whether resources are overwritten when they already exist in destination
     *
     * @throws IOException on I/O error
     * @throws HttpException on HTTP error
     * @throws DavException on WebDAV error or HTTPS -> HTTP redirect
     */
    @Throws(IOException::class, HttpException::class, DavException::class)
    fun move(destination: HttpUrl, overwrite: Boolean, callback: ResponseCallback) {
        val requestBuilder = Request.Builder()
                .method("MOVE", null)
                .header("Content-Length", "0")
                .header("Destination", destination.toString())

        if (!overwrite)      // RFC 4918 9.9.3 and 10.6, default value: T
            requestBuilder.header("Overwrite", "F")

        followRedirects {
            requestBuilder.url(location)
            httpClient.newCall(requestBuilder
                    .build())
                    .execute()
        }.use { response ->
            checkStatus(response)
            if (response.code == HTTP_MULTISTATUS)
                /* Multiple resources were to be affected by the MOVE, but errors on some
                of them prevented the operation from taking place.
                [_] (RFC 4918 9.9.4. Status Codes for MOVE Method) */
                throw HttpException(response)

            // update location
            location.resolve(response.header("Location") ?: destination.toString())?.let {
                location = it
            }

            callback.onResponse(response)
        }
    }

    /**
     * Sends a COPY request for this resource. Follows up to [MAX_REDIRECTS] redirects.
     *
     * @param destination where the resource shall be copied to
     * @param overwrite whether resources are overwritten when they already exist in destination
     *
     * @throws IOException on I/O error
     * @throws HttpException on HTTP error
     * @throws DavException on WebDAV error or HTTPS -> HTTP redirect
     */
    @Throws(IOException::class, HttpException::class, DavException::class)
    fun copy(destination:HttpUrl, overwrite: Boolean, callback: ResponseCallback) {
        val requestBuilder = Request.Builder()
                .method("COPY", null)
                .header("Content-Length", "0")
                .header("Destination", destination.toString())

        if (!overwrite)      // RFC 4918 9.9.3 and 10.6, default value: T
            requestBuilder.header("Overwrite", "F")

        followRedirects {
            requestBuilder.url(location)
            httpClient.newCall(requestBuilder
                    .build())
                    .execute()
        }.use{ response ->
            checkStatus(response)

            if (response.code == HTTP_MULTISTATUS)
                /* Multiple resources were to be affected by the COPY, but errors on some
                of them prevented the operation from taking place.
                [_] (RFC 4918 9.8.5. Status Codes for COPY Method) */
                throw HttpException(response)

            callback.onResponse(response)
        }
    }

    /**
     * Sends a MKCOL request to this resource. Follows up to [MAX_REDIRECTS] redirects.
     * Because the target [location] is by definition a collection, a trailing slash
     * is appended (unless [location] already has a trailing slash).
     *
     * @param xmlBody optional request body (used for MKCALENDAR or Extended MKCOL)
     * @param method HTTP MKCOL method (`MKCOL` by default, may for instance be `MKCALENDAR`)
     * @param headers additional headers to send with the request
     * @param callback called for the response
     *
     * @throws IOException on I/O error
     * @throws HttpException on HTTP error
     * @throws DavException on HTTPS -> HTTP redirect
     */
    @Throws(IOException::class, HttpException::class)
    fun mkCol(xmlBody: String?, method: String = "MKCOL", headers: Headers? = null, callback: ResponseCallback) {
        val rqBody = xmlBody?.toRequestBody(MIME_XML)

        val request = Request.Builder()
            .method(method, rqBody)
            .url(UrlUtils.withTrailingSlash(location))

        if (headers != null)
            request.headers(headers)

        followRedirects {
            httpClient.newCall(request.build()).execute()
        }.use { response ->
            checkStatus(response)
            callback.onResponse(response)
        }
    }

    /**
     * Sends a HEAD request to the resource.
     *
     * Follows up to [MAX_REDIRECTS] redirects.
     *
     * @param callback called with server response unless an exception is thrown
     *
     * @throws IOException on I/O error
     * @throws HttpException on HTTP error
     * @throws DavException on HTTPS -> HTTP redirect
     */
    fun head(callback: ResponseCallback) {
        followRedirects {
            httpClient.newCall(
                Request.Builder()
                    .head()
                    .url(location)
                    .build()
            ).execute()
        }.use { response ->
            checkStatus(response)
            callback.onResponse(response)
        }
    }

    /**
     * Sends a GET request to the resource. Follows up to [MAX_REDIRECTS] redirects.
     *
     * Note: Add `Accept-Encoding: identity` to [headers] if you want to disable compression
     * (compression might change the returned ETag).
     *
     * @param accept   value of `Accept` header (always sent for clarity; use *&#47;* if you don't care)
     * @param headers  additional headers to send with the request
     *
     * @return okhttp Response – **caller is responsible for closing it!**
     *
     * @throws IOException on I/O error
     * @throws HttpException on HTTP error
     * @throws DavException on HTTPS -> HTTP redirect
     */
    fun get(accept: String, headers: Headers?): Response =
        followRedirects {
            val request = Request.Builder()
                .get()
                .url(location)

            if (headers != null)
                request.headers(headers)

            // always Accept header
            request.header("Accept", accept)

            httpClient.newCall(request.build()).execute()
        }

    /**
     * Sends a GET request to the resource. Sends `Accept-Encoding: identity` to disable
     * compression, because compression might change the ETag.
     *
     * Follows up to [MAX_REDIRECTS] redirects.
     *
     * @param accept   value of `Accept` header (always sent for clarity; use *&#47;* if you don't care)
     * @param callback called with server response unless an exception is thrown
     *
     * @throws IOException on I/O error
     * @throws HttpException on HTTP error
     * @throws DavException on HTTPS -> HTTP redirect
     */
    @Deprecated("Use get(accept, headers, callback) with explicit Accept-Encoding instead")
    @Throws(IOException::class, HttpException::class)
    fun get(accept: String, callback: ResponseCallback) {
        get(accept, Headers.headersOf("Accept-Encoding", "identity"), callback)
    }

    /**
     * Sends a GET request to the resource. Follows up to [MAX_REDIRECTS] redirects.
     *
     * Note: Add `Accept-Encoding: identity` to [headers] if you want to disable compression
     * (compression might change the returned ETag).
     *
     * @param accept   value of `Accept` header (always sent for clarity; use *&#47;* if you don't care)
     * @param headers  additional headers to send with the request
     * @param callback called with server response unless an exception is thrown
     *
     * @throws IOException on I/O error
     * @throws HttpException on HTTP error
     * @throws DavException on HTTPS -> HTTP redirect
     */
    fun get(accept: String, headers: Headers?, callback: ResponseCallback) {
        get(accept, headers).use { response ->
            checkStatus(response)
            callback.onResponse(response)
        }
    }

    /**
     * Sends a GET request to the resource for a specific byte range. Make sure to check the
     * response code: servers may return the whole resource with 200 or partials with 206.
     *
     * Follows up to [MAX_REDIRECTS] redirects.
     *
     * @param accept   value of `Accept` header (always sent for clarity; use *&#47;* if you don't care)
     * @param offset   zero-based index of first byte to request
     * @param size     number of bytes to request
     * @param headers  additional headers to send with the request
     * @param callback called with server response unless an exception is thrown
     *
     * @throws IOException on I/O error
     * @throws HttpException on HTTP error
     * @throws DavException on high-level errors
     */
    @Throws(IOException::class, HttpException::class)
    fun getRange(accept: String, offset: Long, size: Int, headers: Headers? = null, callback: ResponseCallback) {
        followRedirects {
            val request = Request.Builder()
                .get()
                .url(location)

            if (headers != null)
                request.headers(headers)

            val lastIndex = offset + size - 1
            request
                .header("Accept", accept)
                .header("Range", "bytes=$offset-$lastIndex")

            httpClient.newCall(request.build()).execute()
        }.use { response ->
            checkStatus(response)
            callback.onResponse(response)
        }
    }

    /**
     * Sends a GET request to the resource. Follows up to [MAX_REDIRECTS] redirects.
     */
    @Throws(IOException::class, HttpException::class)
    fun post(body: RequestBody, ifNoneMatch: Boolean = false, headers: Headers? = null, callback: ResponseCallback) {
        followRedirects {
            val builder = Request.Builder()
                .post(body)
                .url(location)

            if (ifNoneMatch)
            // don't overwrite anything existing
                builder.header("If-None-Match", "*")

            if (headers != null)
                builder.headers(headers)

            httpClient.newCall(builder.build()).execute()
        }.use { response ->
            checkStatus(response)
            callback.onResponse(response)
        }
    }

    /**
     * Sends a PUT request to the resource. Follows up to [MAX_REDIRECTS] redirects.
     *
     * When the server returns an ETag, it is stored in response properties.
     *
     * @param body          new resource body to upload
     * @param ifETag        value of `If-Match` header to set, or null to omit
     * @param ifScheduleTag value of `If-Schedule-Tag-Match` header to set, or null to omit
     * @param ifNoneMatch   indicates whether `If-None-Match: *` ("don't overwrite anything existing") header shall be sent
     * @param headers       additional headers to send
     * @param callback      called with server response unless an exception is thrown
     *
     * @throws IOException on I/O error
     * @throws HttpException on HTTP error
     * @throws DavException on HTTPS -> HTTP redirect
     */
    @Throws(IOException::class, HttpException::class)
    fun put(
        body: RequestBody,
        ifETag: String? = null,
        ifScheduleTag: String? = null,
        ifNoneMatch: Boolean = false,
        headers: Map<String, String> = emptyMap(),
        callback: ResponseCallback
    ) {
        followRedirects {
            val builder = Request.Builder()
                    .put(body)
                    .url(location)

            if (ifETag != null)
                // only overwrite specific version
                builder.header("If-Match", QuotedStringUtils.asQuotedString(ifETag))
            if (ifScheduleTag != null)
                // only overwrite specific version
                builder.header("If-Schedule-Tag-Match", QuotedStringUtils.asQuotedString(ifScheduleTag))
            if (ifNoneMatch)
                // don't overwrite anything existing
                builder.header("If-None-Match", "*")

            // Add custom headers
            for ((key, value) in headers)
                builder.header(key, value)

            httpClient.newCall(builder.build()).execute()
        }.use { response ->
            checkStatus(response)
            callback.onResponse(response)
        }
    }

    /**
     * Sends a DELETE request to the resource. Warning: Sending this request to a collection will
     * delete the collection with all its contents!
     *
     * Follows up to [MAX_REDIRECTS] redirects.
     *
     * @param ifETag        value of `If-Match` header to set, or null to omit
     * @param ifScheduleTag value of `If-Schedule-Tag-Match` header to set, or null to omit
     * @param headers       additional headers to send
     * @param callback      called with server response unless an exception is thrown
     *
     * @throws IOException on I/O error
     * @throws HttpException on HTTP errors, or when 207 Multi-Status is returned
     *         (because then there was probably a problem with a member resource)
     * @throws DavException on HTTPS -> HTTP redirect
     */
    @Throws(IOException::class, HttpException::class)
    fun delete(
        ifETag: String? = null,
        ifScheduleTag: String? = null,
        headers: Map<String, String> = emptyMap(),
        callback: ResponseCallback
    ) {
        followRedirects {
            val builder = Request.Builder()
                    .delete()
                    .url(location)
            if (ifETag != null)
                builder.header("If-Match", QuotedStringUtils.asQuotedString(ifETag))
            if (ifScheduleTag != null)
                builder.header("If-Schedule-Tag-Match", QuotedStringUtils.asQuotedString(ifScheduleTag))

            // Add custom headers
            for ((key, value) in headers)
                builder.header(key, value)

            httpClient.newCall(builder.build()).execute()
        }.use { response ->
            checkStatus(response)

            if (response.code == HTTP_MULTISTATUS)
                /* If an error occurs deleting a member resource (a resource other than
                   the resource identified in the Request-URI), then the response can be
                   a 207 (Multi-Status). […] (RFC 4918 9.6.1. DELETE for Collections) */
                throw HttpException(response)

            callback.onResponse(response)
        }
    }

    /**
     * Sends a PROPFIND request to the resource. Expects and processes a 207 Multi-Status response.
     *
     * Follows up to [MAX_REDIRECTS] redirects.
     *
     * @param depth    "Depth" header to send (-1 for `infinity`)
     * @param reqProp  properties to request
     * @param callback called for every XML response element in the Multi-Status response
     *
     * @throws IOException on I/O error
     * @throws HttpException on HTTP error
     * @throws DavException on WebDAV error (like no 207 Multi-Status response) or HTTPS -> HTTP redirect
     */
    @Throws(IOException::class, HttpException::class, DavException::class)
    fun propfind(depth: Int, vararg reqProp: Property.Name, callback: MultiResponseCallback) {
        // build XML request body
        val serializer = XmlUtils.newSerializer()
        val writer = StringWriter()
        serializer.setOutput(writer)
        serializer.setPrefix("", WebDAV.NS_WEBDAV)
        serializer.setPrefix("CAL", CalDAV.NS_CALDAV)
        serializer.setPrefix("CARD", CardDAV.NS_CARDDAV)
        serializer.startDocument("UTF-8", null)
        serializer.insertTag(WebDAV.PropFind) {
            insertTag(WebDAV.Prop) {
                for (prop in reqProp)
                    insertTag(prop)
            }
        }
        serializer.endDocument()

        followRedirects {
            httpClient.newCall(Request.Builder()
                    .url(location)
                    .method("PROPFIND", writer.toString().toRequestBody(MIME_XML))
                    .header("Depth", if (depth >= 0) depth.toString() else "infinity")
                    .build()).execute()
        }.use {
            processMultiStatus(it, callback)
        }
    }

    /**
     * Sends a PROPPATCH request to the server in order to set and remove properties.
     *
     * @param setProperties     map of properties that shall be set (values currently have to be strings)
     * @param removeProperties  list of names of properties that shall be removed
     * @param callback  called for every XML response element in the Multi-Status response
     *
     * Follows up to [MAX_REDIRECTS] redirects.
     *
     * Currently expects a 207 Multi-Status response although servers are allowed to
     * return other values, too.
     *
     * @throws IOException on I/O error
     * @throws HttpException on HTTP error
     * @throws DavException on WebDAV error (like no 207 Multi-Status response) or HTTPS -> HTTP redirect
     */
    fun proppatch(
        setProperties: Map<Property.Name, String>,
        removeProperties: List<Property.Name>,
        callback: MultiResponseCallback
    ) {
        followRedirects {
            val rqBody = createProppatchXml(setProperties, removeProperties)

            httpClient.newCall(
                Request.Builder()
                    .url(location)
                    .method("PROPPATCH", rqBody.toRequestBody(MIME_XML))
                    .build()
            ).execute()
        }.use {
            // TODO handle not only 207 Multi-Status
            // http://www.webdav.org/specs/rfc4918.html#PROPPATCH-status

            processMultiStatus(it, callback)
        }
    }

    /**
     * Sends a SEARCH request (RFC 5323) with the given body to the server.
     *
     * Follows up to [MAX_REDIRECTS] redirects. Expects a 207 Multi-Status response.
     *
     * @param search    search request body (XML format, DAV:searchrequest or DAV:query-schema-discovery)
     * @param callback  called for every XML response element in the Multi-Status response
     *
     * @throws IOException on I/O error
     * @throws HttpException on HTTP error
     * @throws DavException on WebDAV error (like no 207 Multi-Status response) or HTTPS -> HTTP redirect
     */
    fun search(search: String, callback: MultiResponseCallback) {
        followRedirects {
            httpClient.newCall(Request.Builder()
                .url(location)
                .method("SEARCH", search.toRequestBody(MIME_XML))
                .build()).execute()
        }.use {
            processMultiStatus(it, callback)
        }
    }


    // status handling

    /**
     * Checks the status from an HTTP response and throws an exception in case of an error.
     *
     * @throws HttpException in case of an HTTP error
     */
    protected fun checkStatus(response: Response) {
        if (response.code / 100 == 2)
            // everything OK
            return

        throw when (response.code) {
            401 -> UnauthorizedException(response)
            403 -> ForbiddenException(response)
            404 -> NotFoundException(response)
            409 -> ConflictException(response)
            410 -> GoneException(response)
            412 -> PreconditionFailedException(response)
            503 -> ServiceUnavailableException(response)
            else -> HttpException(response)
        }
    }

    /**
     * Send a request and follows up to [MAX_REDIRECTS] redirects.
     *
     * @param sendRequest called to send the request (may be called multiple times)
     *
     * @return response of the last request (whether it is a redirect or not)
     *
     * @throws DavException on HTTPS -> HTTP redirect
     */
    internal fun followRedirects(sendRequest: () -> Response): Response {
        lateinit var response: Response
        for (attempt in 1..MAX_REDIRECTS) {
            response = sendRequest()
            if (response.isRedirect)
                // handle 3xx Redirection
                response.use {
                    val target = it.header("Location")?.let { location.resolve(it) }
                    if (target != null) {
                        logger.fine("Redirected, new location = $target")

                        if (location.isHttps && !target.isHttps)
                            throw DavException("Received redirect from HTTPS to HTTP")

                        location = target
                    } else
                        throw DavException("Redirected without new Location")
                }
            else
                break
        }
        return response
    }

    /**
     * Validates a 207 Multi-Status response.
     *
     * @param response will be checked for Multi-Status response
     *
     * @throws DavException if the response is not a Multi-Status response
     */
    fun assertMultiStatus(response: Response) {
        if (response.code != HTTP_MULTISTATUS)
            throw DavException("Expected 207 Multi-Status, got ${response.code} ${response.message}", response = response)

        response.peekBody(XML_SIGNATURE.size.toLong()).use { body ->
            body.contentType()?.let { mimeType ->
                if (((mimeType.type != "application" && mimeType.type != "text")) || mimeType.subtype != "xml") {
                    /* Content-Type is not application/xml or text/xml although that is expected here.
                       Some broken servers return an XML response with some other MIME type. So we try to see
                       whether the response is maybe XML although the Content-Type is something else. */
                    try {
                        response.peekBody(XML_SIGNATURE.size.toLong()).use { body ->
                            if (XML_SIGNATURE.contentEquals(body.bytes())) {
                                logger.warning("Received 207 Multi-Status that seems to be XML but has MIME type $mimeType")

                                // response is OK, return and do not throw Exception below
                                return
                            }
                        }
                    } catch (e: Exception) {
                        logger.log(Level.WARNING, "Couldn't scan for XML signature", e)
                    }

                    throw DavException("Received non-XML 207 Multi-Status", response = response)
                }
            } ?: logger.warning("Received 207 Multi-Status without Content-Type, assuming XML")
        }
    }


    // Multi-Status handling

    /**
     * Processes a Multi-Status response.
     *
     * @param response response which is expected to contain a Multi-Status response
     * @param callback called for every XML response element in the Multi-Status response
     *
     * @return list of properties which have been received in the Multi-Status response, but
     * are not part of response XML elements (like `sync-token` which is returned as [SyncToken])
     *
     * @throws IOException on I/O error
     * @throws HttpException on HTTP error
     * @throws DavException on WebDAV error (for instance, when the response is not a Multi-Status response)
     */
    protected fun processMultiStatus(response: Response, callback: MultiResponseCallback): List<Property> {
        checkStatus(response)
        assertMultiStatus(response)
        return response.body.use {
            processMultiStatus(it.charStream(), callback)
        }
    }

    /**
     * Processes a Multi-Status response.
     *
     * @param reader   the Multi-Status response is read from this
     * @param callback called for every XML response element in the Multi-Status response
     *
     * @return list of properties which have been received in the Multi-Status response, but
     * are not part of response XML elements (like `sync-token` which is returned as [SyncToken])
     *
     * @throws IOException on I/O error
     * @throws HttpException on HTTP error
     * @throws DavException on WebDAV error (like an invalid XML response)
     */
    protected fun processMultiStatus(reader: Reader, callback: MultiResponseCallback): List<Property> {
        val responseProperties = mutableListOf<Property>()
        val parser = XmlUtils.newPullParser()

        fun parseMultiStatus(): List<Property> {
            // <!ELEMENT multistatus (response*, responsedescription?,
            //                        sync-token?) >
            val depth = parser.depth
            var eventType = parser.eventType
            while (!(eventType == XmlPullParser.END_TAG && parser.depth == depth)) {
                if (eventType == XmlPullParser.START_TAG && parser.depth == depth + 1)
                    when (parser.propertyName()) {
                        WebDAV.Response ->
                            DavResponse.Companion.parse(parser, location, callback)
                        WebDAV.SyncToken ->
                            XmlReader(parser).readText()?.let {
                                responseProperties += SyncToken(it)
                            }
                    }
                eventType = parser.next()
            }

            return responseProperties
        }

        try {
            parser.setInput(reader)

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.depth == 1)
                    if (parser.propertyName() == WebDAV.MultiStatus)
                        return parseMultiStatus()
                // ignore further <multistatus> elements
                eventType = parser.next()
            }

            throw DavException("Multi-Status response didn't contain multistatus XML element")

        } catch (e: EOFException) {
            throw DavException("Incomplete multistatus XML element", e)
        } catch (e: XmlPullParserException) {
            throw DavException("Couldn't parse multistatus XML element", e)
        }
    }

}
