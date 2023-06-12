/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm

import at.bitfire.dav4jvm.XmlUtils.insertTag
import at.bitfire.dav4jvm.exception.*
import at.bitfire.dav4jvm.property.SyncToken
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.logging.*
import io.ktor.utils.io.bits.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import io.ktor.utils.io.core.EOFException
import io.ktor.utils.io.errors.*
import nl.adaptivity.xmlutil.EventType
import nl.adaptivity.xmlutil.QName
import nl.adaptivity.xmlutil.XmlException
import nl.adaptivity.xmlutil.XmlStreaming
import kotlin.coroutines.cancellation.CancellationException
import kotlin.jvm.JvmOverloads
import at.bitfire.dav4jvm.Response as DavResponse

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
 * @param log           will be used for logging
 */
open class DavResource @JvmOverloads constructor(
    val httpClient: HttpClient,
    location: Url,
    val log: Logger = Dav4jvm.log
) {

    companion object {
        const val MAX_REDIRECTS = 5

        const val HTTP_MULTISTATUS = 207
        val MIME_XML = ContentType.Application.Xml.withCharset(Charsets.UTF_8)

        val PROPFIND = QName(XmlUtils.NS_WEBDAV, "propfind")
        val PROPERTYUPDATE = QName(XmlUtils.NS_WEBDAV, "propertyupdate")
        val SET = QName(XmlUtils.NS_WEBDAV, "set")
        val REMOVE = QName(XmlUtils.NS_WEBDAV, "remove")
        val PROP = QName(XmlUtils.NS_WEBDAV, "prop")
        val HREF = QName(XmlUtils.NS_WEBDAV, "href")

        val XML_SIGNATURE = "<?xml".toByteArray()

        //HTTP Methods
        val Move = HttpMethod("MOVE")
        val Copy = HttpMethod("COPY")
        val MKCol = HttpMethod("MKCOL")
        val Propfind = HttpMethod("PROPFIND")
        val Proppatch = HttpMethod("PROPPATCH")
        val Search = HttpMethod("SEARCH")
        val Report = HttpMethod("REPORT")


        /**
         * Creates a request body for the PROPPATCH request.
         */
        internal fun createProppatchXml(
            setProperties: Map<QName, String>,
            removeProperties: List<QName>
        ): String {
            // build XML request body
            val writer = StringBuilder()
            val serializer = XmlStreaming.newWriter(writer)
            serializer.setPrefix("d", XmlUtils.NS_WEBDAV)
            serializer.startDocument(encoding = "UTF-8")
            serializer.insertTag(PROPERTYUPDATE) {
                // DAV:set
                if (setProperties.isNotEmpty()) {
                    serializer.insertTag(SET) {
                        for (prop in setProperties) {
                            serializer.insertTag(PROP) {
                                serializer.insertTag(prop.key) {
                                    text(prop.value)
                                }
                            }
                        }
                    }
                }

                // DAV:remove
                if (removeProperties.isNotEmpty()) {
                    serializer.insertTag(REMOVE) {
                        for (prop in removeProperties) {
                            insertTag(PROP) {
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
    var location: Url
        private set             // allow internal modification only (for redirects)

    init {
        // Don't follow redirects (only useful for GET/POST).
        // This means we have to handle 30x responses ourselves.
        require(httpClient.pluginOrNull(HttpRedirect) == null) { "httpClient must not follow redirects automatically" }

        this.location = location
    }

    override fun toString() = location.toString()


    /**
     * Gets the file name of this resource. See [HttpUtils.fileName] for details.
     */
    fun fileName() = HttpUtils.fileName(location)


    /**
     * Sends an OPTIONS request to this resource without HTTP compression (because some servers have
     * broken compression for OPTIONS). Doesn't follow redirects.
     *
     * @param callback called with server response unless an exception is thrown
     *
     * @throws IOException on I/O error
     * @throws HttpException on HTTP error
     * @throws DavException on HTTPS -> HTTP redirect
     */
    @Throws(IOException::class, HttpException::class, CancellationException::class)
    suspend fun options(callback: CapabilitiesCallback) {
        val response = httpClient.request(HttpRequestBuilder().apply {
            method = HttpMethod.Options
            header("Content-Length", "0")
            url(location)
            header("Accept-Encoding", "identity")      // disable compression
        })
        checkStatus(response)
        callback.onCapabilities(
            HttpUtils.listHeader(response, "DAV").map { it.trim() }.toSet(),
            response
        )
    }

    /**
     * Sends a MOVE request to this resource. Follows up to [MAX_REDIRECTS] redirects.
     * Updates [location] on success.
     *
     * @param destination where the resource shall be moved to
     * @param forceOverride whether resources are overwritten when they already exist in destination
     *
     * @throws IOException on I/O error
     * @throws HttpException on HTTP error
     * @throws DavException on WebDAV error or HTTPS -> HTTP redirect
     */
    @Throws(IOException::class, HttpException::class, DavException::class, CancellationException::class)
    suspend fun move(destination: Url, forceOverride: Boolean, callback: ResponseCallback) {
        val requestBuilder = HttpRequestBuilder().apply {
            method = Move
            header("Content-Length", "0")
            header("Destination", destination.toString())
        }

        if (forceOverride) requestBuilder.header("Overwrite", "F")

        //TODO emulate followRedirects
        requestBuilder.url(location)
        val response = httpClient.request(requestBuilder)
        checkStatus(response)
        if (response.status == HttpStatusCode.MultiStatus)
        /* Multiple resources were to be affected by the MOVE, but errors on some
        of them prevented the operation from taking place.
        [_] (RFC 4918 9.9.4. Status Codes for MOVE Method) */
            throw HttpException(response)

        // update location
        val nPath = response.headers[HttpHeaders.Location] ?: destination.toString()
        location = URLBuilder(location).takeFrom(nPath).build()

        callback.onResponse(response)

    }

    /**
     * Sends a COPY request for this resource. Follows up to [MAX_REDIRECTS] redirects.
     *
     * @param destination where the resource shall be copied to
     * @param forceOverride whether resources are overwritten when they already exist in destination
     *
     * @throws IOException on I/O error
     * @throws HttpException on HTTP error
     * @throws DavException on WebDAV error or HTTPS -> HTTP redirect
     */
    @Throws(IOException::class, HttpException::class, DavException::class, CancellationException::class)
    suspend fun copy(destination: Url, forceOverride: Boolean, callback: ResponseCallback) {
        val requestBuilder = HttpRequestBuilder().apply {
            method = Copy
            header("Content-Length", "0")
            header("Destination", destination.toString())
        }

        if (forceOverride) requestBuilder.header("Overwrite", "F")

        //TODO followRedirects
        requestBuilder.url(location)
        val response = httpClient.request(
            requestBuilder
        )
        checkStatus(response)

        if (response.status == HttpStatusCode.MultiStatus)
        /* Multiple resources were to be affected by the COPY, but errors on some
        of them prevented the operation from taking place.
        [_] (RFC 4918 9.8.5. Status Codes for COPY Method) */
            throw HttpException(response)

        callback.onResponse(response)
    }

    /**
     * Sends a MKCOL request to this resource. Follows up to [MAX_REDIRECTS] redirects.
     *
     * @throws IOException on I/O error
     * @throws HttpException on HTTP error
     * @throws DavException on HTTPS -> HTTP redirect
     */
    @Throws(IOException::class, HttpException::class, CancellationException::class)
    suspend fun mkCol(xmlBody: String?, callback: ResponseCallback) {

        //TODO followRedirects {
        val response = httpClient.request(
            HttpRequestBuilder().apply {
                method = MKCol
                setBody(xmlBody)
                header(HttpHeaders.ContentType, MIME_XML)
                url(location)
            }
        )
        checkStatus(response)
        callback.onResponse(response)
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
    suspend fun head(callback: ResponseCallback) {
        //TODO followRedirects {
        val response = httpClient.request(
            HttpRequestBuilder().apply {
                method = HttpMethod.Head
                url(location)
            }
        )

        checkStatus(response)
        callback.onResponse(response)

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
    @Throws(IOException::class, HttpException::class, CancellationException::class)
    suspend fun get(accept: String, callback: ResponseCallback) =
        get(accept, Headers.build { append(HttpHeaders.AcceptEncoding, "identity") }, callback)

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
    suspend fun get(accept: String, headers: Headers?, callback: ResponseCallback) {
        //TODO followRedirects {
        val request = HttpRequestBuilder().apply {
            method = HttpMethod.Get
            url(location)
        }

        if (headers != null)
            request.headers.appendAll(headers)

        // always Accept header
        request.header(HttpHeaders.Accept, accept)

        val response = httpClient.request(request)
        checkStatus(response)
        callback.onResponse(response)
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
    @Throws(IOException::class, HttpException::class, CancellationException::class)
    suspend fun getRange(
        accept: String,
        offset: Long,
        size: Int,
        headers: Headers? = null,
        callback: ResponseCallback
    ) {
        //TODO followRedirects {
        val request = HttpRequestBuilder().apply {
            method = HttpMethod.Get
            url(location)
        }


        if (headers != null)
            request.headers.appendAll(headers)

        val lastIndex = offset + size - 1
        request.header(HttpHeaders.Accept, accept)
        request.header(HttpHeaders.Range, "bytes=$offset-$lastIndex")

        val response = httpClient.request(request)
        checkStatus(response)
        callback.onResponse(response)
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
     * @param callback      called with server response unless an exception is thrown
     *
     * @throws IOException on I/O error
     * @throws HttpException on HTTP error
     * @throws DavException on HTTPS -> HTTP redirect
     */
    @Throws(IOException::class, HttpException::class, CancellationException::class)
    suspend fun put(
        body: Any,
        contentType: ContentType,
        ifETag: String? = null,
        ifScheduleTag: String? = null,
        ifNoneMatch: Boolean = false,
        callback: ResponseCallback
    ) {
        //TODO followRedirects {
        val builder = HttpRequestBuilder().apply {
            header(HttpHeaders.ContentType, contentType)
            setBody(body)
            url(location)
        }

        if (ifETag != null)
        // only overwrite specific version
            builder.header(HttpHeaders.IfMatch, QuotedStringUtils.asQuotedString(ifETag))
        if (ifScheduleTag != null)
        // only overwrite specific version
            builder.header(HttpHeaders.IfScheduleTagMatch, QuotedStringUtils.asQuotedString(ifScheduleTag))
        if (ifNoneMatch)
        // don't overwrite anything existing
            builder.header(HttpHeaders.IfNoneMatch, "*")

        val response = httpClient.request(builder)
        checkStatus(response)
        callback.onResponse(response)
    }

    /**
     * Sends a DELETE request to the resource. Warning: Sending this request to a collection will
     * delete the collection with all its contents!
     *
     * Follows up to [MAX_REDIRECTS] redirects.
     *
     * @param ifETag        value of `If-Match` header to set, or null to omit
     * @param ifScheduleTag value of `If-Schedule-Tag-Match` header to set, or null to omit
     * @param callback      called with server response unless an exception is thrown
     *
     * @throws IOException on I/O error
     * @throws HttpException on HTTP errors, or when 207 Multi-Status is returned
     *         (because then there was probably a problem with a member resource)
     * @throws DavException on HTTPS -> HTTP redirect
     */
    @Throws(IOException::class, HttpException::class, CancellationException::class)
    suspend fun delete(ifETag: String? = null, ifScheduleTag: String? = null, callback: ResponseCallback) {
        //TODO followRedirects {
        val builder = HttpRequestBuilder().apply {
            method = HttpMethod.Delete
            url(location)
        }

        if (ifETag != null)
            builder.header("If-Match", QuotedStringUtils.asQuotedString(ifETag))
        if (ifScheduleTag != null)
            builder.header("If-Schedule-Tag-Match", QuotedStringUtils.asQuotedString(ifScheduleTag))

        val response = httpClient.request(builder)
        checkStatus(response)

        if (response.status == HttpStatusCode.MultiStatus)
        /* If an error occurs deleting a member resource (a resource other than
           the resource identified in the Request-URI), then the response can be
           a 207 (Multi-Status). […] (RFC 4918 9.6.1. DELETE for Collections) */
            throw HttpException(response)

        callback.onResponse(response)
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
    @Throws(IOException::class, HttpException::class, DavException::class, CancellationException::class)
    suspend fun propfind(depth: Int, vararg reqProp: QName, callback: MultiResponseCallback) {
        // build XML request body
        val writer = StringBuilder()
        val serializer = XmlStreaming.newWriter(writer)
        serializer.setPrefix("", XmlUtils.NS_WEBDAV)
        serializer.setPrefix("CAL", XmlUtils.NS_CALDAV)
        serializer.setPrefix("CARD", XmlUtils.NS_CARDDAV)
        serializer.startDocument(encoding = "UTF-8")
        serializer.insertTag(PROPFIND) {
            insertTag(PROP) {
                for (prop in reqProp)
                    insertTag(prop)
            }
        }
        serializer.endDocument()

        //TODO followRedirects {
        val response = httpClient.request {
            url(location)
            method = Propfind
            setBody(writer.toString())
            header(HttpHeaders.ContentType, MIME_XML)
            header("Depth", if (depth >= 0) depth.toString() else "infinity")
        }
        processMultiStatus(response, callback)
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
    suspend fun proppatch(
        setProperties: Map<QName, String>,
        removeProperties: List<QName>,
        callback: MultiResponseCallback
    ) {
        //TODO followRedirects {
        val rqBody = createProppatchXml(setProperties, removeProperties)

        val response = httpClient.request {

            url(location)
            method = Proppatch
            setBody(rqBody)
            header(HttpHeaders.ContentType, MIME_XML)
        }
        // TODO handle not only 207 Multi-Status
        // http://www.webdav.org/specs/rfc4918.html#PROPPATCH-status

        processMultiStatus(response, callback)
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
    suspend fun search(search: String, callback: MultiResponseCallback) {
        ///TODO followRedirects {
        val response = httpClient.request {
            url(location)
            method = Search
            setBody(search)
            header(HttpHeaders.ContentType, MIME_XML)
        }
        processMultiStatus(response, callback)
    }


    // status handling

    /**
     * Checks the status from an HTTP response and throws an exception in case of an error.
     *
     * @throws HttpException (with XML error names, if available) in case of an HTTP error
     */
    protected fun checkStatus(response: HttpResponse) {
        val status = response.status
        if (status.isSuccess())
        // everything OK
            return

        throw when (status) {
            HttpStatusCode.Unauthorized -> UnauthorizedException(response)

            HttpStatusCode.Forbidden -> ForbiddenException(response)

            HttpStatusCode.NotFound -> NotFoundException(response)

            HttpStatusCode.Conflict -> ConflictException(response)

            HttpStatusCode.PreconditionFailed -> PreconditionFailedException(response)

            HttpStatusCode.ServiceUnavailable -> ServiceUnavailableException(response)

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
    //TODO figure out if this is needed or if we can rely on ktor redirect follow
    /*internal fun followRedirects(sendRequest: () -> Response): Response {
        HttpRedirect.HttpResponseRedirect
        lateinit var response: Response
        for (attempt in 1..MAX_REDIRECTS) {
            response = sendRequest()
            if (response.isRedirect)
            // handle 3xx Redirection
                response.use {
                    val target = it.header("Location")?.let { location.resolve(it) }
                    if (target != null) {
                        log.fine("Redirected, new location = $target")

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
    }*/

    /**
     * Validates a 207 Multi-Status response.
     *
     * @param response will be checked for Multi-Status response
     *
     * @throws DavException if the response is not a Multi-Status response
     */
    suspend fun assertMultiStatus(response: HttpResponse) {
        if (response.status != HttpStatusCode.MultiStatus)
            throw DavException(
                "Expected 207 Multi-Status, got ${response.status}",
                httpResponse = response
            )

        val contentType = response.contentType()
        contentType?.let { mimeType ->
            if ((mimeType.match(ContentType.Application.Any) && mimeType.match(ContentType.Text.Any)) || mimeType.match(
                    ContentType("*", "xml")
                )
            ) {
                /* Content-Type is not application/xml or text/xml although that is expected here.
                   Some broken servers return an XML response with some other MIME type. So we try to see
                   whether the response is maybe XML although the Content-Type is something else. */
                try {
                    val firstBytes = ByteArray(XML_SIGNATURE.size)
                    withMemory(XML_SIGNATURE.size) { memory ->
                        response.bodyAsChannel().peekTo(memory, 0)
                        memory.loadByteArray(0, firstBytes)
                    }
                    if (XML_SIGNATURE.contentEquals(firstBytes)) {
                        Dav4jvm.log.warn("Received 207 Multi-Status that seems to be XML but has MIME type $mimeType")

                        // response is OK, return and do not throw Exception below
                        return
                    }
                } catch (e: Exception) {
                    Dav4jvm.log.warn("Couldn't scan for XML signature", e)
                }

                throw DavException("Received non-XML 207 Multi-Status", httpResponse = response)
            }
        } ?: log.warn("Received 207 Multi-Status without Content-Type, assuming XML")
    }


    // Multi-Status handling

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
    protected suspend fun processMultiStatus(response: HttpResponse, callback: MultiResponseCallback): List<Property> {
        checkStatus(response)
        assertMultiStatus(response)
        val responseProperties = mutableListOf<Property>()
        val parser = XmlStreaming.newReader(response.bodyAsText())

        fun parseMultiStatus(): List<Property> {
            // <!ELEMENT multistatus (response*, responsedescription?,
            //                        sync-token?) >
            val depth = parser.depth
            var eventType = parser.eventType
            while (!(eventType == EventType.END_ELEMENT && parser.depth == depth)) {
                if (eventType == EventType.START_ELEMENT && parser.depth == depth + 1)
                    when (parser.name) {
                        DavResponse.RESPONSE ->
                            at.bitfire.dav4jvm.Response.parse(parser, location, callback)

                        SyncToken.NAME ->
                            XmlUtils.readText(parser)?.let {
                                responseProperties += SyncToken(it)
                            }
                    }
                eventType = parser.next()
            }

            return responseProperties
        }

        try {

            var eventType = parser.eventType
            while (eventType != EventType.END_DOCUMENT) {
                if (eventType == EventType.START_ELEMENT && parser.depth == 1)
                    if (parser.name == DavResponse.MULTISTATUS)
                        return parseMultiStatus()
                // ignore further <multistatus> elements
                eventType = parser.next()
            }

            throw DavException("Multi-Status response didn't contain multistatus XML element")

        } catch (e: EOFException) {
            throw DavException("Incomplete multistatus XML element", e)
        } catch (e: XmlException) {
            throw DavException("Couldn't parse multistatus XML element", e)
        }
    }

}
