/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm

import at.bitfire.dav4jvm.exception.*
import at.bitfire.dav4jvm.property.SyncToken
import okhttp3.*
import okhttp3.Response
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.EOFException
import java.io.IOException
import java.io.Reader
import java.io.StringWriter
import java.net.HttpURLConnection
import java.util.logging.Logger

/**
 * Represents a WebDAV resource at the given location and allows WebDAV
 * requests to be performed on this resource.
 *
 * Requests are executed synchronously (blocking). If no error occurs, the given
 * callback will be called. Otherwise, an exception is thrown. *These callbacks
 * don't need to close the response.*
 *
 * @param httpClient    [OkHttpClient] to access this object (must not follow redirects)
 * @param location      location of the WebDAV resource
 * @param log           will be used for logging
 */
open class DavResource @JvmOverloads constructor(
        val httpClient: OkHttpClient,
        location: HttpUrl,
        val log: Logger = Constants.log
) {

    companion object {
        const val MAX_REDIRECTS = 5
        val MIME_XML = MediaType.parse("application/xml; charset=utf-8")
    }

    /**
     * URL of this resource (changes when being redirected by server)
     */
    var location: HttpUrl
        private set             // allow internal modification only (for redirects)

    init {
        // Don't follow redirects (only useful for GET/POST).
        // This means we have to handle 30x responses ourselves.
        require(!httpClient.followRedirects()) { "httpClient must not follow redirects automatically" }

        this.location = location
    }

    override fun toString() = location.toString()


    /**
     * Gets the file name of this resource. See [HttpUtils.fileName] for details.
     */
    fun fileName() = HttpUtils.fileName(location)


    /**
     * Sends an OPTIONS request to this resource. Doesn't follow redirects.
     *
     * @param callback called with server response unless an exception is thrown
     *
     * @throws IOException on I/O error
     * @throws HttpException on HTTP error
     */
    @Throws(IOException::class, HttpException::class)
    fun options(callback: (davCapabilities: Set<String>, response: Response) -> Unit) {
        httpClient.newCall(Request.Builder()
                .method("OPTIONS", null)
                .header("Content-Length", "0")
                .url(location)
                .build()).execute().use { response ->
            checkStatus(response)
            callback(HttpUtils.listHeader(response, "DAV").map { it.trim() }.toSet(), response)
        }
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
     * @throws DavException on WebDAV error
     */
    @Throws(IOException::class, HttpException::class, DavException::class)
    fun move(destination: HttpUrl, forceOverride: Boolean, callback: (response: Response) -> Unit) {
        val requestBuilder = Request.Builder()
                .method("MOVE", null)
                .header("Content-Length", "0")
                .header("Destination", destination.toString())

        if (forceOverride) requestBuilder.header("Overwrite", "F")

        followRedirects {
            requestBuilder.url(location)
            httpClient.newCall(requestBuilder
                    .build())
                    .execute()
        }.use { response ->
            checkStatus(response)
            if (response.code() == 207)
                /* Multiple resources were to be affected by the MOVE, but errors on some
                of them prevented the operation from taking place.
                [_] (RFC 4918 9.9.4. Status Codes for MOVE Method) */
                throw HttpException(response)

            // update location
            location.resolve(response.header("Location") ?: destination.toString())?.let {
                location = it
            }

            callback(response)
        }
    }

    /**
     * Sends a COPY request for this resource. Follows up to [MAX_REDIRECTS] redirects.
     *
     * @param destination where the resource shall be copied to
     * @param forceOverride whether resources are overwritten when they already exist in destination
     *
     * @throws IOException on I/O error
     * @throws HttpException on HTTP error
     * @throws DavException on WebDAV error
     */
    @Throws(IOException::class, HttpException::class, DavException::class)
    fun copy(destination:HttpUrl, forceOverride:Boolean, callback: (response: Response) -> Unit) {
        val requestBuilder = Request.Builder()
                .method("COPY", null)
                .header("Content-Length", "0")
                .header("Destination", destination.toString())

        if(forceOverride) requestBuilder.header("Overwrite", "F")

        followRedirects {
            requestBuilder.url(location)
            httpClient.newCall(requestBuilder
                    .build())
                    .execute()
        }.use{ response ->
            checkStatus(response)

            if (response.code() == 207)
            /* Multiple resources were to be affected by the COPY, but errors on some
            of them prevented the operation from taking place.
            [_] (RFC 4918 9.8.5. Status Codes for COPY Method) */
                throw HttpException(response)

            callback(response)
        }
    }

    /**
     * Sends a MKCOL request to this resource. Follows up to [MAX_REDIRECTS] redirects.
     *
     * @throws IOException on I/O error
     * @throws HttpException on HTTP error
     */
    @Throws(IOException::class, HttpException::class)
    fun mkCol(xmlBody: String?, callback: (response: Response) -> Unit) {
        val rqBody = if (xmlBody != null) RequestBody.create(MIME_XML, xmlBody) else null

        followRedirects {
            httpClient.newCall(Request.Builder()
                    .method("MKCOL", rqBody)
                    .url(location)
                    .build()).execute()
        }.use { response ->
            checkStatus(response)
            callback(response)
        }
    }

    /**
     * Sends a GET request to the resource. Sends `Accept-Encoding: identity` to disable
     * compression, because compression might change the ETag.
     *
     * Follows up to [MAX_REDIRECTS] redirects.
     *
     * @param accept   value of Accept header (must not be null, but may be *&#47;*)
     * @param callback called with server response unless an exception is thrown
     *
     * @throws IOException on I/O error
     * @throws HttpException on HTTP error
     */
    @Throws(IOException::class, HttpException::class)
    fun get(accept: String, callback: (response: Response) -> Unit) {
        followRedirects {
            httpClient.newCall(Request.Builder()
                    .get()
                    .url(location)
                    .header("Accept", accept)
                    .header("Accept-Encoding", "identity")    // disable compression because it can change the ETag
                    .build()).execute()
        }.use { response ->
            checkStatus(response)
            callback(response)
        }
    }

    /**
     * Sends a PUT request to the resource. Follows up to [MAX_REDIRECTS] redirects.
     *
     * When the server returns an ETag, it is stored in response properties.
     *
     * @param body         new resource body to upload
     * @param ifMatchETag  value of `If-Match` header to set, or null to omit
     * @param ifNoneMatch  indicates whether `If-None-Match: *` ("don't overwrite anything existing") header shall be sent
     * @param callback     called with server response unless an exception is thrown
     *
     * @throws IOException on I/O error
     * @throws HttpException on HTTP error
     */
    @Throws(IOException::class, HttpException::class)
    fun put(body: RequestBody, ifMatchETag: String?, ifNoneMatch: Boolean, callback: (Response) -> Unit) {
        followRedirects {
            val builder = Request.Builder()
                    .put(body)
                    .url(location)

            if (ifMatchETag != null)
                // only overwrite specific version
                builder.header("If-Match", QuotedStringUtils.asQuotedString(ifMatchETag))
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
     * Sends a DELETE request to the resource. Warning: Sending this request to a collection will
     * delete the collection with all its contents!
     *
     * Follows up to [MAX_REDIRECTS] redirects.
     *
     * @param ifMatchETag  value of `If-Match` header to set, or null to omit
     * @param callback     called with server response unless an exception is thrown
     *
     * @throws IOException on I/O error
     * @throws HttpException on HTTP errors, or when 207 Multi-Status is returned
     *         (because then there was probably a problem with a member resource)
     */
    @Throws(IOException::class, HttpException::class)
    fun delete(ifMatchETag: String?, callback: (Response) -> Unit) {
        followRedirects {
            val builder = Request.Builder()
                    .delete()
                    .url(location)
            if (ifMatchETag != null)
                builder.header("If-Match", QuotedStringUtils.asQuotedString(ifMatchETag))

            httpClient.newCall(builder.build()).execute()
        }.use { response ->
            checkStatus(response)

            if (response.code() == 207)
            /* If an error occurs deleting a member resource (a resource other than
               the resource identified in the Request-URI), then the response can be
               a 207 (Multi-Status). […] (RFC 4918 9.6.1. DELETE for Collections) */
                throw HttpException(response)

            callback(response)
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
     * @throws DavException on WebDAV error (like no 207 Multi-Status response)
     */
    @Throws(IOException::class, HttpException::class, DavException::class)
    fun propfind(depth: Int, vararg reqProp: Property.Name, callback: DavResponseCallback) {
        // build XML request body
        val serializer = XmlUtils.newSerializer()
        val writer = StringWriter()
        serializer.setOutput(writer)
        serializer.setPrefix("", XmlUtils.NS_WEBDAV)
        serializer.setPrefix("CAL", XmlUtils.NS_CALDAV)
        serializer.setPrefix("CARD", XmlUtils.NS_CARDDAV)
        serializer.startDocument("UTF-8", null)
        serializer.startTag(XmlUtils.NS_WEBDAV, "propfind")
        serializer.startTag(XmlUtils.NS_WEBDAV, "prop")
        for (prop in reqProp) {
            serializer.startTag(prop.namespace, prop.name)
            serializer.endTag(prop.namespace, prop.name)
        }
        serializer.endTag(XmlUtils.NS_WEBDAV, "prop")
        serializer.endTag(XmlUtils.NS_WEBDAV, "propfind")
        serializer.endDocument()

        followRedirects {
            httpClient.newCall(Request.Builder()
                    .url(location)
                    .method("PROPFIND", RequestBody.create(MIME_XML, writer.toString()))
                    .header("Depth", if (depth >= 0) depth.toString() else "infinity")
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
    private fun checkStatus(response: Response) =
            checkStatus(response.code(), response.message(), response)

    /**
     * Checks the status from an HTTP response and throws an exception in case of an error.
     *
     * @throws HttpException (with XML error names, if available) in case of an HTTP error
     */
    private fun checkStatus(code: Int, message: String?, response: Response?) {
        if (code / 100 == 2)
            // everything OK
            return

        throw when (code) {
            HttpURLConnection.HTTP_UNAUTHORIZED ->
                if (response != null) UnauthorizedException(response) else UnauthorizedException(message)
            HttpURLConnection.HTTP_FORBIDDEN ->
                if (response != null) ForbiddenException(response) else ForbiddenException(message)
            HttpURLConnection.HTTP_NOT_FOUND ->
                if (response != null) NotFoundException(response) else NotFoundException(message)
            HttpURLConnection.HTTP_CONFLICT ->
                if (response != null) ConflictException(response) else ConflictException(message)
            HttpURLConnection.HTTP_PRECON_FAILED ->
                if (response != null) PreconditionFailedException(response) else PreconditionFailedException(message)
            HttpURLConnection.HTTP_UNAVAILABLE ->
                if (response != null) ServiceUnavailableException(response) else ServiceUnavailableException(message)
            else ->
                if (response != null) HttpException(response) else HttpException(code, message)
        }
    }

    /**
     * Send a request and follows up to [MAX_REDIRECTS] redirects.
     *
     * @param sendRequest called to send the request (may be called multiple times)
     *
     * @return response of the last request (whether it is a redirect or not)
     */
    protected fun followRedirects(sendRequest: () -> Response): Response {
        lateinit var response: Response
        for (attempt in 1..MAX_REDIRECTS) {
            response = sendRequest()
            if (response.isRedirect)
            // handle 3xx Redirection
                response.use {
                    val target = it.header("Location")?.let { location.resolve(it) }
                    if (target != null) {
                        log.fine("Redirected, new location = $target")
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
     * Asserts a Multi-Status response.
     *
     * @param response will be checked for Multi-Status response
     *
     * @throws DavException if the response is not a Multi-Status response
     */
    private fun assertMultiStatus(response: Response) {
        if (response.code() != 207)
            throw DavException("Expected 207 Multi-Status, got ${response.code()} ${response.message()}")

        if (response.body() == null)
            throw DavException("Received 207 Multi-Status without body")

        response.body()?.contentType()?.let {
            if (((it.type() != "application" && it.type() != "text")) || it.subtype() != "xml")
                throw DavException("Received non-XML 207 Multi-Status")
        } ?: log.warning("Received 207 Multi-Status without Content-Type, assuming XML")
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
    protected fun processMultiStatus(response: Response, callback: DavResponseCallback): List<Property> {
        checkStatus(response)
        assertMultiStatus(response)
        response.body()!!.use {
            return processMultiStatus(it.charStream(), callback)
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
    protected fun processMultiStatus(reader: Reader, callback: DavResponseCallback): List<Property> {
        val responseProperties = mutableListOf<Property>()
        val parser = XmlUtils.newPullParser()

        fun parseMultiStatus(): List<Property> {
            // <!ELEMENT multistatus (response*, responsedescription?,
            //                        sync-token?) >
            val depth = parser.depth
            var eventType = parser.eventType
            while (!(eventType == XmlPullParser.END_TAG && parser.depth == depth)) {
                if (eventType == XmlPullParser.START_TAG && parser.depth == depth + 1 && parser.namespace == XmlUtils.NS_WEBDAV)
                    when (parser.name) {
                        "response" ->
                            at.bitfire.dav4jvm.Response.parse(parser, location, callback)
                        "sync-token" ->
                            XmlUtils.readText(parser)?.let {
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
                    if (parser.namespace == XmlUtils.NS_WEBDAV && parser.name == "multistatus")
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