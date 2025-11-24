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
import at.bitfire.dav4jvm.XmlUtils.propertyName
import at.bitfire.dav4jvm.ktor.DavResource.Companion.MAX_REDIRECTS
import at.bitfire.dav4jvm.ktor.exception.DavException
import at.bitfire.dav4jvm.ktor.exception.HttpException
import at.bitfire.dav4jvm.property.caldav.CalDAV
import at.bitfire.dav4jvm.property.carddav.CardDAV
import at.bitfire.dav4jvm.property.webdav.SyncToken
import at.bitfire.dav4jvm.property.webdav.WebDAV
import io.ktor.client.HttpClient
import io.ktor.client.plugins.compression.compress
import io.ktor.client.request.header
import io.ktor.client.request.prepareDelete
import io.ktor.client.request.prepareGet
import io.ktor.client.request.prepareHead
import io.ktor.client.request.prepareOptions
import io.ktor.client.request.preparePost
import io.ktor.client.request.preparePut
import io.ktor.client.request.prepareRequest
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.HttpStatement
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.URLParserException
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.http.isSecure
import io.ktor.http.isSuccess
import io.ktor.http.takeFrom
import io.ktor.http.withCharset
import io.ktor.util.IdentityEncoder
import io.ktor.util.logging.Logger
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.jvm.javaio.toInputStream
import io.ktor.utils.io.peek
import kotlinx.io.bytestring.encodeToByteString
import org.slf4j.LoggerFactory
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.EOFException
import java.io.IOException
import java.io.StringWriter


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
 * ATTENTION: dav4jvm handles redirects itself. Make sure followRedirects is set to FALSE for the httpClient.
 *
 * @param httpClient    [HttpClient] to access this object (must not follow redirects)
 * @param location      location of the WebDAV resource
 * @param logger        will be used for logging
 */
open class DavResource(
    val httpClient: HttpClient,
    location: Url,
    val logger: Logger = LoggerFactory.getLogger(DavResource::class.java.name)
) {

    companion object {
        const val MAX_REDIRECTS = 5

        val MIME_XML_UTF8 = ContentType.Application.Xml.withCharset(Charsets.UTF_8)

        val XML_SIGNATURE = "<?xml".encodeToByteString()


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
    var location: Url = location
        private set             // allow internal modification only (for redirects)


    /**
     * File name of this resource (determined from [location])
     */
    val fileName
        get() = KtorHttpUtils.fileName(location)


    /**
     * Sends an OPTIONS request to this resource without HTTP compression (because some servers have
     * broken compression for OPTIONS). Follows up to [MAX_REDIRECTS] redirects when set.
     *
     * @param followRedirects   whether redirects should be followed (default: *false*)
     * @param callback          called with server response on success
     *
     * @throws IOException on I/O error
     * @throws HttpException on HTTP error
     * @throws DavException on HTTPS -> HTTP redirect
     */
    suspend fun options(followRedirects: Boolean = false, callback: CapabilitiesCallback) {
        if (followRedirects)
            followRedirects(
                prepareRequest = ::prepareOptionsRequest,
                callback = { response ->
                    processOptionsResponse(response, callback)
                }
            )
        else {
            prepareOptionsRequest().execute { response ->
                processOptionsResponse(response, callback)
            }
        }
    }

    private suspend fun prepareOptionsRequest(): HttpStatement =
        httpClient.prepareOptions(location) {
            // explicitly set Content-Length although OPTIONS has no request body (for compatibility)
            header(HttpHeaders.ContentLength, "0")

            // explicitly disable compression (for compatibility)
            compress(IdentityEncoder.name)
        }

    private suspend fun processOptionsResponse(response: HttpResponse, callback: CapabilitiesCallback) {
        // check for success
        checkStatus(response)

        val capabilities = KtorHttpUtils.listHeader(response, "DAV")
        callback.onCapabilities(
            capabilities.map { it.trim() }.toSet(),
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
    suspend fun move(destination: Url, overwrite: Boolean, callback: ResponseCallback) {
        followRedirects({
            httpClient.prepareRequest(location) {
                method = HttpMethod.parse("MOVE")

                header(HttpHeaders.ContentLength, "0")
                header(HttpHeaders.Destination, destination.toString())
                if (!overwrite)      // RFC 4918 9.9.3 and 10.6, default value: T
                    header(HttpHeaders.Overwrite, "F")
            }
        }) { response ->
            checkStatus(response)

            if (response.status == HttpStatusCode.MultiStatus) {
                /* Multiple resources were to be affected by the MOVE, but errors on some
                of them prevented the operation from taking place.
                [_] (RFC 4918 9.9.4. Status Codes for MOVE Method) */
                throw HttpException.fromResponse(response)
            }

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
    suspend fun copy(destination: Url, overwrite: Boolean, callback: ResponseCallback) {
        followRedirects({
            httpClient.prepareRequest(location) {
                method = HttpMethod.parse("COPY")

                header(HttpHeaders.ContentLength, "0")
                header(HttpHeaders.Destination, destination.toString())
                if (!overwrite)      // RFC 4918 9.9.3 and 10.6, default value: T
                    header("Overwrite", "F")
            }
        }) { response ->
            checkStatus(response)

            if (response.status == HttpStatusCode.MultiStatus)
                /* Multiple resources were to be affected by the COPY, but errors on some
                of them prevented the operation from taking place.
                [_] (RFC 4918 9.8.5. Status Codes for COPY Method) */
                throw HttpException.fromResponse(response)

            callback.onResponse(response)
        }
    }

    /**
     * Sends a MKCOL request to this resource. Follows up to [MAX_REDIRECTS] redirects.
     * Because the target [location] is by definition a collection, a trailing slash
     * is appended (unless [location] already has a trailing slash).
     *
     * @param xmlBody           optional request body (used for MKCALENDAR or Extended MKCOL)
     * @param methodName        HTTP MKCOL method (`MKCOL` by default, may for instance be `MKCALENDAR`)
     * @param additionalHeaders additional headers to send with the request
     * @param callback          called with server response on success
     *
     * @throws IOException on I/O error
     * @throws HttpException on HTTP error
     * @throws DavException on HTTPS -> HTTP redirect
     */
    suspend fun mkCol(
        xmlBody: String?,
        methodName: String = "MKCOL",
        additionalHeaders: Headers? = null,
        callback: ResponseCallback
    ) {
        followRedirects({
            httpClient.prepareRequest(UrlUtils.withTrailingSlash(location)) {
                method = HttpMethod.parse(methodName)

                if (additionalHeaders != null)
                    headers.appendAll(additionalHeaders)

                contentType(MIME_XML_UTF8)
                setBody(xmlBody)
            }
        }) { response ->
            checkStatus(response)
            callback.onResponse(response)
        }
    }

    /**
     * Sends a HEAD request to the resource.
     *
     * Follows up to [MAX_REDIRECTS] redirects.
     *
     * @param callback              called with server response on success
     *
     * @throws IOException on I/O error
     * @throws HttpException on HTTP error
     * @throws DavException on HTTPS -> HTTP redirect
     */
    suspend fun head(callback: ResponseCallback) {
        followRedirects({
            httpClient.prepareHead(location)
        }) { response ->
            checkStatus(response)
            callback.onResponse(response)
        }
    }

    /**
     * Sends a GET request to the resource.
     *
     * Follows up to [MAX_REDIRECTS] redirects.
     *
     * @param additionalHeaders     additional headers to send with the request (at least [HttpHeaders.Accept] is recommended)
     * @param disableCompression    whether compression shall be disabled (because it may change the returned ETag)
     * @param callback              called with server response on success
     *
     * @throws IOException on I/O error
     * @throws HttpException on HTTP error
     * @throws DavException on HTTPS -> HTTP redirect
     */
    suspend fun get(
        additionalHeaders: Headers? = null,
        disableCompression: Boolean = false,
        callback: ResponseCallback
    ) {
        followRedirects({
            httpClient.prepareGet(location) {
                if (additionalHeaders != null)
                    headers.appendAll(additionalHeaders)

                if (disableCompression)
                    compress(IdentityEncoder.name)
            }
        }) { response ->
            checkStatus(response)
            callback.onResponse(response)
        }
    }

    /**
     * Sends a ranged GET request to the resource for a specific byte range.
     *
     * Make sure to check the response code in the callback because servers may
     * return partials with 206, but also the whole resource with 200.
     *
     * Follows up to [MAX_REDIRECTS] redirects.
     *
     * @param offset            zero-based index of first byte to request
     * @param size              number of bytes to request
     * @param additionalHeaders additional headers to send with the request (at least [HttpHeaders.Accept] is recommended)
     * @param callback          called with server response on success
     *
     * @throws IOException on I/O error
     * @throws HttpException on HTTP error
     * @throws DavException on high-level errors
     */
    suspend fun getRange(offset: Long, size: Int, additionalHeaders: Headers? = null, callback: ResponseCallback) {
        followRedirects({
            httpClient.prepareGet(location) {
                val lastIndex = offset + size - 1
                header(HttpHeaders.Range, "bytes=$offset-$lastIndex")

                if (additionalHeaders != null)
                    headers.appendAll(additionalHeaders)
            }
        }) { response ->
            checkStatus(response)
            callback.onResponse(response)
        }
    }

    /**
     * Sends a POST request to the resource.
     *
     * Follows up to [MAX_REDIRECTS] redirects.
     *
     * @param provideBody       resource body to upload (unconsumed, may be called multiple times on redirects)
     * @param mimeType          content type of resource body
     * @param additionalHeaders additional headers to send
     * @param callback          called with server response on success
     */
    suspend fun post(
        provideBody: () -> ByteReadChannel,
        mimeType: ContentType,
        additionalHeaders: Headers? = null,
        callback: ResponseCallback
    ) {
        followRedirects({
            httpClient.preparePost(location) {
                if (additionalHeaders != null)
                    headers.appendAll(additionalHeaders)

                contentType(mimeType)
                setBody(provideBody())
            }
        }) { response ->
            checkStatus(response)
            callback.onResponse(response)
        }
    }

    /**
     * Sends a PUT request to the resource.
     *
     * Follows up to [MAX_REDIRECTS] redirects.
     *
     * @param provideBody       resource body to upload (unconsumed, may be called multiple times on redirects)
     * @param mimeType          content type of resource body
     * @param additionalHeaders additional headers to send (like [HttpHeaders.IfNoneMatch] to prevent overwriting)
     * @param callback          called with server response on success
     *
     * @throws IOException on I/O error
     * @throws HttpException on HTTP error
     * @throws DavException on HTTPS -> HTTP redirect
     */
    suspend fun put(
        provideBody: () -> ByteReadChannel,
        mimeType: ContentType,
        additionalHeaders: Headers? = null,
        callback: ResponseCallback
    ) {
        followRedirects({
            httpClient.preparePut(location) {
                if (additionalHeaders != null)
                    headers.appendAll(additionalHeaders)

                contentType(mimeType)
                setBody(provideBody())
            }
        }) { response ->
            checkStatus(response)
            callback.onResponse(response)
        }
    }

    /**
     * Sends a DELETE request to the resource.
     *
     * Follows up to [MAX_REDIRECTS] redirects.
     *
     * @param additionalHeaders additional headers to send
     * @param callback          called with server response on success
     *
     * @throws IOException      on I/O error
     * @throws HttpException    on HTTP errors, or when 207 Multi-Status is returned
     *                          (because then there was probably a problem with a member resource)
     * @throws DavException     on HTTPS -> HTTP redirect
     */
    suspend fun delete(additionalHeaders: Headers? = null, callback: ResponseCallback) {
        followRedirects({
            httpClient.prepareDelete(location) {
                if (additionalHeaders != null)
                    headers.appendAll(additionalHeaders)
            }
        }) { response ->
            checkStatus(response)

            if (response.status == HttpStatusCode.MultiStatus)
                /* If an error occurs deleting a member resource (a resource other than
                   the resource identified in the Request-URI), then the response can be
                   a 207 (Multi-Status). […] (RFC 4918 9.6.1. DELETE for Collections) */
                throw HttpException.fromResponse(response)

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
    suspend fun propfind(depth: Int, vararg reqProp: Property.Name, callback: MultiResponseCallback) {
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

        followRedirects({
            httpClient.prepareRequest(location) {
                method = HttpMethod.parse("PROPFIND")

                header(HttpHeaders.Depth, if (depth >= 0) depth.toString() else "infinity")

                contentType(MIME_XML_UTF8)
                setBody(writer.toString())
            }
        }) { response ->
            processMultiStatus(response, callback)
        }
    }

    /**
     * Sends a PROPPATCH request to the server in order to set and remove properties.
     *
     * Follows up to [MAX_REDIRECTS] redirects.
     *
     * Currently expects a 207 Multi-Status response although servers are allowed to
     * return other values, too.
     *
     * @param setProperties     map of properties that shall be set (values currently have to be strings)
     * @param removeProperties  list of names of properties that shall be removed
     * @param callback          called for every XML response element in the Multi-Status response
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

        followRedirects({
            httpClient.prepareRequest(location) {
                method = HttpMethod.parse("PROPPATCH")

                contentType(MIME_XML_UTF8)
                setBody(rqBody)
            }
        }) { response ->
            // room for further improvement: handle not only 207 Multi-Status
            // http://www.webdav.org/specs/rfc4918.html#PROPPATCH-status

            processMultiStatus(response, callback)
        }
    }

    /**
     * Sends a SEARCH request (RFC 5323) with the given body to the server.
     *
     * Follows up to [MAX_REDIRECTS] redirects.
     *
     * Expects a 207 Multi-Status response.
     *
     * @param search    search request body (in XML format; like `DAV:searchrequest` or `DAV:query-schema-discovery`)
     * @param callback  called for every XML response element in the Multi-Status response
     *
     * @throws IOException on I/O error
     * @throws HttpException on HTTP error
     * @throws DavException on WebDAV error (like no 207 Multi-Status response) or HTTPS -> HTTP redirect
     */
    suspend fun search(search: String, callback: MultiResponseCallback) {
        followRedirects({
            httpClient.prepareRequest(location) {
                method = HttpMethod.parse("SEARCH")

                contentType(MIME_XML_UTF8)
                setBody(search)
            }
        }) { response ->
            processMultiStatus(response, callback)
        }
    }


    // status handling

    /**
     * Checks the status from an HTTP response and throws a specific exception in case of an error.
     *
     * @throws HttpException in case of an HTTP error
     */
    protected suspend fun checkStatus(response: HttpResponse) {
        if (response.status.isSuccess())
            return      // everything OK

        throw HttpException.fromResponse(response)
    }

    /**
     * Send a request and follows up to [MAX_REDIRECTS] redirects.
     *
     * @param prepareRequest    prepares the request (may be called multiple times with updated [location])
     * @param callback          called for the final resource that is not redirected anymore
     *                          (may never be called if there are too many redirects)
     *
     * @throws DavException     on invalid redirects or when the number of redirects has reached [MAX_REDIRECTS]
     */
    internal suspend fun followRedirects(prepareRequest: suspend () -> HttpStatement, callback: ResponseCallback) {
        var redirects = 0
        var finished = false
        while (!finished) {
            prepareRequest().execute { response ->
                val isRedirect = response.status in arrayOf(
                    HttpStatusCode.MovedPermanently,
                    HttpStatusCode.Found,
                    HttpStatusCode.TemporaryRedirect,
                    HttpStatusCode.PermanentRedirect
                )
                if (isRedirect) {
                    if (++redirects >= MAX_REDIRECTS)
                        throw DavException("Too many redirects")

                    // take new location from response header
                    val newLocation = response.headers[HttpHeaders.Location]
                        ?: throw DavException("Redirected without new Location")

                    // resolve possible relative location URL
                    val destination = try {
                        URLBuilder(location)
                            .takeFrom(newLocation)
                            .build()
                    } catch (e: URLParserException) {
                        throw DavException("Redirected to invalid Location", cause = e)
                    }

                    // block insecure redirects
                    if (location.protocol.isSecure() && !destination.protocol.isSecure())
                        throw DavException("Received redirect from HTTPS to HTTP")

                    // save new location
                    location = destination

                } else {
                    // no redirect, pass scoped response to callback
                    callback.onResponse(response)

                    // quit loop (we can't use break because we're in the scoped execute() callback)
                    finished = true
                }
            }
        }
    }


    // Multi-Status handling

    /**
     * Validates a 207 Multi-Status response.
     *
     * @param httpResponse  response that will be checked for Multi-Status
     * @param bodyChannel   response body channel that will be peeked into in order to
     *                      determine whether it's XML
     *
     * @throws DavException if the response is not a Multi-Status response with XML body
     */
    suspend fun assertMultiStatus(httpResponse: HttpResponse, bodyChannel: ByteReadChannel) {
        if (httpResponse.status != HttpStatusCode.MultiStatus)
            throw DavException.fromResponse(
                message = "Expected 207 Multi-Status, got ${httpResponse.status}",
                response = httpResponse,
                responseBodyChannel = bodyChannel
            )

        val contentType = httpResponse.contentType()
        if (contentType == null) {
            logger.warn("Received 207 Multi-Status without Content-Type, assuming XML")
            return  // supposed XML response body, fine
        }

        if (contentType.isXml())
            return  // reported XML response body, fine

        /* Content-Type is not application/xml or text/xml although that is expected here.
           Some broken servers return an XML response with some other MIME type. So we try to see
           whether the response is maybe XML although the Content-Type is something else. */
        try {
            val firstBytes = bodyChannel.peek(XML_SIGNATURE.size)
            if (firstBytes == XML_SIGNATURE) {
                logger.warn("Received 207 Multi-Status that seems to be XML but has MIME type $contentType")
                return  // response body starts with XML signature, fine
            }
        } catch (e: Exception) {
            logger.warn("Couldn't scan for XML signature", e)
        }

        // non-XML response body
        throw DavException.fromResponse(
            message = "Received non-XML 207 Multi-Status",
            response = httpResponse
        )
    }

    /**
     * Processes a Multi-Status response.
     *
     * @param response unconsumed response which is expected to contain a Multi-Status response
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
        val bodyChannel = response.bodyAsChannel()

        // verify that the response is 207 Multi-Status
        assertMultiStatus(response, bodyChannel)

        val parser = XmlUtils.newPullParser()

        try {
            bodyChannel.toInputStream().use { stream ->
                parser.setInput(stream, null)

                var eventType = parser.eventType
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG && parser.depth == 1)
                        if (parser.propertyName() == WebDAV.MultiStatus) {
                            return MultiStatusParser(location, callback).parseResponse(parser)
                            // further <multistatus> elements are ignored
                        }

                    eventType = parser.next()
                }
            }

            throw DavException("Multi-Status response didn't contain multistatus XML element")

        } catch (e: EOFException) {
            throw DavException("Incomplete multistatus XML element", cause = e)
        } catch (e: XmlPullParserException) {
            throw DavException("Couldn't parse multistatus XML element", cause = e)
        }
    }

}