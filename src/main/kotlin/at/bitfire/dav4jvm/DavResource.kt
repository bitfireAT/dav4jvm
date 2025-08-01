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

import at.bitfire.dav4jvm.DavResource.Companion.MAX_REDIRECTS
import at.bitfire.dav4jvm.XmlUtils.insertTag
import at.bitfire.dav4jvm.XmlUtils.propertyName
import at.bitfire.dav4jvm.exception.ConflictException
import at.bitfire.dav4jvm.exception.DavException
import at.bitfire.dav4jvm.exception.ForbiddenException
import at.bitfire.dav4jvm.exception.HttpException
import at.bitfire.dav4jvm.exception.NotFoundException
import at.bitfire.dav4jvm.exception.PreconditionFailedException
import at.bitfire.dav4jvm.exception.ServiceUnavailableException
import at.bitfire.dav4jvm.exception.UnauthorizedException
import at.bitfire.dav4jvm.property.caldav.NS_CALDAV
import at.bitfire.dav4jvm.property.carddav.NS_CARDDAV
import at.bitfire.dav4jvm.property.webdav.NS_WEBDAV
import at.bitfire.dav4jvm.property.webdav.SyncToken
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequest
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.header
import io.ktor.client.request.prepareRequest
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.append
import io.ktor.http.cio.Request
import io.ktor.http.contentLength
import io.ktor.http.contentType
import io.ktor.http.headers
import io.ktor.http.isSecure
import io.ktor.http.takeFrom
import io.ktor.http.withCharset
import io.ktor.util.appendAll
import io.ktor.util.logging.Logger
import io.ktor.utils.io.core.readFully
import io.ktor.utils.io.jvm.javaio.toInputStream
import io.ktor.utils.io.readBuffer
import org.slf4j.LoggerFactory
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.EOFException
import java.io.IOException
import java.io.Reader
import java.io.StringWriter
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
 * @param httpClient    [HttpClient] to access this object (must not follow redirects)
 * @param location      location of the WebDAV resource
 * @param logger        will be used for logging
 */
open class DavResource @JvmOverloads constructor(
    val httpClient: HttpClient,
    location: Url,
    val logger: Logger = LoggerFactory.getLogger(DavResource::class.java.name)
) {

    companion object {
        const val MAX_REDIRECTS = 5

        val MIME_XML = ContentType.Application.Xml.withCharset(Charsets.UTF_8)

        val PROPFIND = Property.Name(NS_WEBDAV, "propfind")
        val PROPERTYUPDATE = Property.Name(NS_WEBDAV, "propertyupdate")
        val SET = Property.Name(NS_WEBDAV, "set")
        val REMOVE = Property.Name(NS_WEBDAV, "remove")
        val PROP = Property.Name(NS_WEBDAV, "prop")
        val HREF = Property.Name(NS_WEBDAV, "href")

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
            serializer.setPrefix("d", NS_WEBDAV)
            serializer.startDocument("UTF-8", null)
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

        //TODO: Restore  the require(!httpClient.followRedirects) part here somehow!
        //require(!httpClient.followRedirects) { "httpClient must not follow redirects automatically" }
        this.location = location
    }

    override fun toString() = location.toString()


    /**
     * Gets the file name of this resource. See [HttpUtils.fileName] for details.
     */
    fun fileName() = HttpUtils.fileName(location)


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
    suspend fun options(followRedirects: Boolean = false, callback: CapabilitiesCallback) {
        val request = httpClient.prepareRequest {
            url(location)
            method = HttpMethod.Options
            headers.append(HttpHeaders.ContentLength, "0")
            headers.append(HttpHeaders.AcceptEncoding, "identity")
        }
        val response = if (followRedirects)
            followRedirects { request.execute() }
        else
            request.execute()

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
     * @param overwrite whether resources are overwritten when they already exist in destination
     *
     * @throws IOException on I/O error
     * @throws HttpException on HTTP error
     * @throws DavException on WebDAV error or HTTPS -> HTTP redirect
     */
    @Throws(IOException::class, HttpException::class, DavException::class)
    suspend fun move(destination: Url, overwrite: Boolean, callback: ResponseCallback) {

        followRedirects {
            httpClient.prepareRequest {
                url(location)
                method = HttpMethod.parse("MOVE")
                headers.append(HttpHeaders.ContentLength, "0")
                headers.append(HttpHeaders.Destination, destination.toString())
                if (!overwrite)      // RFC 4918 9.9.3 and 10.6, default value: T
                    headers.append(HttpHeaders.Overwrite, "F")
            }.execute()
        }.let { response ->
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
    suspend fun copy(destination: Url, overwrite: Boolean, callback: ResponseCallback) {

        followRedirects {
            httpClient.prepareRequest {
                url(location)
                method = HttpMethod.parse("COPY")
                headers.append(HttpHeaders.ContentLength, "0")
                headers.append(HttpHeaders.Destination, destination.toString())
                if (!overwrite)      // RFC 4918 9.9.3 and 10.6, default value: T
                    headers.append("Overwrite", "F")
            }.execute()
        }.let { response ->
            checkStatus(response)
            if(response.status == HttpStatusCode.MultiStatus)
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
    suspend fun mkCol(xmlBody: String?, method: String = "MKCOL", headersOptional: Headers? = null, callback: ResponseCallback) {

        followRedirects {
            httpClient.prepareRequest {
                this.method = HttpMethod.parse(method)
                setBody(xmlBody)
                headers.append(HttpHeaders.ContentType, MIME_XML)
                url(UrlUtils.withTrailingSlash(location))
                if (headersOptional != null)
                    headers.appendAll(headersOptional)
            }.execute()
        }.let { response ->
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
    suspend fun head(callback: ResponseCallback) {

        followRedirects {
            httpClient.prepareRequest {
                method = HttpMethod.Head
                url(location)
            }.execute()
        }.let { response ->
            checkStatus(response)
            callback.onResponse(response)
        }

        /*  TODO @ricki, second call omitted, not sure if this was done like that on purpose?
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
         */
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
     * @return HttpResponse
     *
     * @throws IOException on I/O error
     * @throws HttpException on HTTP error
     * @throws DavException on HTTPS -> HTTP redirect
     */
    suspend fun get(accept: String, headers: Headers?): HttpResponse =
    followRedirects {
        httpClient.prepareRequest {
            method = HttpMethod.Get
            url(location)
            if (headers != null)
                this.headers.appendAll(headers)
            header(HttpHeaders.Accept, accept)
        }.execute()
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
        get(accept, headers).let { response ->
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
    suspend fun getRange(accept: String, offset: Long, size: Int, headers: Headers? = null, callback: ResponseCallback) {
        followRedirects {
            httpClient.prepareRequest {
                method = HttpMethod.Get
                url(location)
                if (headers != null)
                    this.headers.appendAll(headers)
                val lastIndex = offset + size - 1
                this.headers.append(HttpHeaders.Accept, accept)
                this.headers.append(HttpHeaders.Range, "bytes=$offset-$lastIndex")
            }.execute()
        }.let { response ->
            checkStatus(response)
            callback.onResponse(response)
        }
    }

    /**
     * Sends a GET request to the resource. Follows up to [MAX_REDIRECTS] redirects.
     */
    @Throws(IOException::class, HttpException::class)
    suspend fun post(body: String, ifNoneMatch: Boolean = false, headers: Headers? = null, callback: ResponseCallback) {

        followRedirects {
            httpClient.prepareRequest {
                method = HttpMethod.Post
                url(location)
                this.setBody(body)    // TODO: check in detail if this is correct, changed to String instead of HttpRequest
                if (ifNoneMatch)
                    this.headers.append(HttpHeaders.IfNoneMatch, "*")
                if (headers?.isEmpty() == false)
                    this.headers.appendAll(headers)
            }.execute()
        }.let { response ->
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
    suspend fun put(
        body: String,      // TODO: Changed to String, maybe a problem for DAVx5? The ContentType is anyway defined in headers
        headers: Headers = HeadersBuilder().build(),
        ifETag: String? = null,
        ifScheduleTag: String? = null,
        ifNoneMatch: Boolean = false,
        callback: ResponseCallback
    ) {

        followRedirects {
            httpClient.prepareRequest {
                method = HttpMethod.Put
                //header(HttpHeaders.ContentType, contentType)
                setBody(body)
                url(location)
                if (ifETag != null)
                // only overwrite specific version
                    header(HttpHeaders.IfMatch, QuotedStringUtils.asQuotedString(ifETag))
                if (ifScheduleTag != null)
                // only overwrite specific version
                    header(HttpHeaders.IfScheduleTagMatch, QuotedStringUtils.asQuotedString(ifScheduleTag))
                if (ifNoneMatch)
                // don't overwrite anything existing
                    header(HttpHeaders.IfNoneMatch, "*")
                // TODO: Check with  Ricki: Is it okay to use just header here? Or should we use append?
            }.execute()
        }.let { response ->
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
    suspend fun delete(
        ifETag: String? = null,
        ifScheduleTag: String? = null,
        headers: Map<String, String> = emptyMap(),
        callback: ResponseCallback
    ) {
        followRedirects {
            httpClient.prepareRequest {
                method = HttpMethod.Delete
                url(location)
                if (ifETag != null)
                    header(HttpHeaders.IfMatch, QuotedStringUtils.asQuotedString(ifETag))
                if (ifScheduleTag != null)
                    header(HttpHeaders.IfScheduleTagMatch, QuotedStringUtils.asQuotedString(ifScheduleTag))
                this.headers.appendAll(headers)   // TODO: check with Ricki if the previous two headers also shouldn't be appended!
            }.execute()
        }.let { response ->
            checkStatus(response)

            if (response.status == HttpStatusCode.MultiStatus)
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
    suspend fun propfind(depth: Int, vararg reqProp: Property.Name, callback: MultiResponseCallback) {
        // build XML request body
        val serializer = XmlUtils.newSerializer()
        val writer = StringWriter()
        serializer.setOutput(writer)
        serializer.setPrefix("", NS_WEBDAV)
        serializer.setPrefix("CAL", NS_CALDAV)
        serializer.setPrefix("CARD", NS_CARDDAV)
        serializer.startDocument("UTF-8", null)
        serializer.insertTag(PROPFIND) {
            insertTag(PROP) {
                for (prop in reqProp)
                    insertTag(prop)
            }
        }
        serializer.endDocument()

        followRedirects {
            httpClient.prepareRequest {
                url(location)
                method = HttpMethod.parse("PROPFIND")
                setBody(writer.toString())
                header(HttpHeaders.ContentType, MIME_XML)
                header(HttpHeaders.Depth, if (depth >= 0) depth.toString() else "infinity")
            }.execute()
        }.let { response ->
            processMultiStatus(response, callback)
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
    suspend fun proppatch(
        setProperties: Map<Property.Name, String>,
        removeProperties: List<Property.Name>,
        callback: MultiResponseCallback
    ) {
        val rqBody = createProppatchXml(setProperties, removeProperties)

        followRedirects {
            httpClient.prepareRequest {
                url(location)
                method = HttpMethod.parse("PROPPATCH")
                setBody(rqBody)
                header(HttpHeaders.ContentType, MIME_XML)
            }.execute()
        }.let { response ->
            // TODO handle not only 207 Multi-Status
            // http://www.webdav.org/specs/rfc4918.html#PROPPATCH-status

            processMultiStatus(response, callback)
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
    suspend fun search(search: String, callback: MultiResponseCallback) {
        followRedirects {
            httpClient.prepareRequest {
                url(location)
                method = HttpMethod.parse("SEARCH")
                setBody(search)
                header(HttpHeaders.ContentType, MIME_XML)
            }.execute()
        }.let { response ->
            processMultiStatus(response, callback)
        }
    }


    // status handling

    /**
     * Checks the status from an HTTP response and throws an exception in case of an error.
     *
     * @throws HttpException in case of an HTTP error
     */
    protected fun checkStatus(response: HttpResponse) =
            checkStatus(response.status, response.status.description, response)   // TODO not sure if response.status.description still makes sense here. If no, the whole method can be removed. TODO: Check with Ricki

    /**
     * Checks the status from an HTTP response and throws an exception in case of an error.
     *
     * @throws HttpException (with XML error names, if available) in case of an HTTP error
     */
    private fun checkStatus(httpStatusCode: HttpStatusCode, message: String?, response: HttpResponse?) {
        if (httpStatusCode.value / 100 == 2)
            // everything OK
            return

        throw when (httpStatusCode) {
            HttpStatusCode.Unauthorized ->
                if (response != null) UnauthorizedException(response) else UnauthorizedException(message)
            HttpStatusCode.Forbidden ->
                if (response != null) ForbiddenException(response) else ForbiddenException(message)
            HttpStatusCode.NotFound ->
                if (response != null) NotFoundException(response) else NotFoundException(message)
            HttpStatusCode.Conflict ->
                if (response != null) ConflictException(response) else ConflictException(message)
            HttpStatusCode.PreconditionFailed ->
                if (response != null) PreconditionFailedException(response) else PreconditionFailedException(message)
            HttpStatusCode.ServiceUnavailable ->
                if (response != null) ServiceUnavailableException(response) else ServiceUnavailableException(message)
            else ->
                if (response != null) HttpException(response) else HttpException(httpStatusCode.value, message)
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
    internal suspend fun followRedirects(sendRequest: suspend () -> HttpResponse): HttpResponse {

        lateinit var response: HttpResponse
        for (attempt in 1..MAX_REDIRECTS) {
            response = sendRequest()
            if (response.status in listOf(
                    HttpStatusCode.PermanentRedirect,
                    HttpStatusCode.TemporaryRedirect,
                    HttpStatusCode.MultipleChoices,
                    HttpStatusCode.MovedPermanently,
                    HttpStatusCode.Found,
                    HttpStatusCode.SeeOther)
                )     //if is redirect, based on okhttp3/Response.kt: HTTP_PERM_REDIRECT, HTTP_TEMP_REDIRECT, HTTP_MULT_CHOICE, HTTP_MOVED_PERM, HTTP_MOVED_TEMP, HTTP_SEE_OTHER
                // handle 3xx Redirection
                response.let {
                    val target = it.headers[HttpHeaders.Location]?.let { newLocation ->
                        URLBuilder(location).takeFrom(newLocation).build()
                    }
                    if (target != null) {
                        logger.info("Redirected, new location = $target")    // TODO: Is logger.info ok here?

                        if (location.protocol.isSecure() && !target.protocol.isSecure())
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
    suspend fun assertMultiStatus(httpResponse: HttpResponse) {
        val response = httpResponse

        if (response.status != HttpStatusCode.MultiStatus)
            throw DavException("Expected 207 Multi-Status, got ${response.status.value} ${response.status.description}", httpResponse = response)

        val bodyChannel = response.bodyAsChannel()

        response.contentType()?.let { mimeType ->          // is response.contentType() ok here? Or must it be the content type of the body?
            if (((!ContentType.Application.contains(mimeType) && !ContentType.Text.contains(mimeType))) || mimeType.contentSubtype != "xml") {
                /* Content-Type is not application/xml or text/xml although that is expected here.
                   Some broken servers return an XML response with some other MIME type. So we try to see
                   whether the response is maybe XML although the Content-Type is something else. */
                try {
                    val firstBytes = ByteArray(XML_SIGNATURE.size)
                    bodyChannel.readBuffer().peek().readFully(firstBytes)
                    if (XML_SIGNATURE.contentEquals(firstBytes)) {
                        logger.warn("Received 207 Multi-Status that seems to be XML but has MIME type $mimeType")

                        // response is OK, return and do not throw Exception below
                        return
                    }
                } catch (e: Exception) {
                    logger.warn("Couldn't scan for XML signature", e)
                }

                throw DavException("Received non-XML 207 Multi-Status", httpResponse = response)
            }
        } ?: logger.warn("Received 207 Multi-Status without Content-Type, assuming XML")
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
    protected suspend fun processMultiStatus(response: HttpResponse, callback: MultiResponseCallback): List<Property> {
        checkStatus(response)
        assertMultiStatus(response)
        return processMultiStatus(response.bodyAsBytes().inputStream().reader(), callback)
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
                        DavResponse.RESPONSE ->
                            at.bitfire.dav4jvm.Response.parse(parser, location, callback)
                        SyncToken.NAME ->
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
                    if (parser.propertyName() == DavResponse.MULTISTATUS)
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
