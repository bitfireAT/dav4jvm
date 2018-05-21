/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4android

import at.bitfire.dav4android.exception.*
import at.bitfire.dav4android.property.GetContentType
import at.bitfire.dav4android.property.GetETag
import at.bitfire.dav4android.property.ResourceType
import at.bitfire.dav4android.property.SyncToken
import okhttp3.*
import okhttp3.internal.http.StatusLine
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.EOFException
import java.io.IOException
import java.io.Reader
import java.io.StringWriter
import java.net.HttpURLConnection
import java.net.ProtocolException
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Represents a WebDAV resource at the given location and allows WebDAV
 * requests to be performed on this resource.
 *
 * @param httpClient    [OkHttpClient] to access this object
 * @param location      location of the WebDAV resource
 * @param log           [Logger] which will be used for logging
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

    var location: HttpUrl
        private set             // allow internal modification only (for redirects)

    init {
        // Don't follow redirects (only useful for GET/POST).
        // This means we have to handle 30x responses manually.
        if (httpClient.followRedirects())
            throw IllegalArgumentException("httpClient must not follow redirects automatically")

        this.location = location
    }

    override fun toString() = location.toString()


    /**
     * The resource name (the last segment of the URL path).
     *
     * @return resource name or `` (empty string) if the URL ends with a slash
     *         (i.e. the resource is a collection).
     */
    fun fileName(): String {
        val pathSegments = location.pathSegments()
        return pathSegments[pathSegments.size - 1]
    }


    /**
     * Sends an OPTIONS request to this resource, requesting [DavResponse.capabilities].
     * Doesn't follow redirects.
     *
     * @return response object with capabilities set as received in HTTP response (must
     *         be closed by caller)
     *
     * @throws IOException on I/O error
     * @throws HttpException on HTTP error
     */
    @Throws(IOException::class, HttpException::class)
    fun options(): DavResponse {
        val response = httpClient.newCall(Request.Builder()
                .method("OPTIONS", null)
                .header("Content-Length", "0")
                .url(location)
                .build()).execute()
        checkStatus(response)

        return DavResponse.Builder(location)
                .capabilities(HttpUtils.listHeader(response, "DAV").map { it.trim() }.toSet())
                .responseBody(response.body())
                .build()
    }

    /**
     * Sends a MKCOL request to this resource. Follows up to [MAX_REDIRECTS] redirects.
     *
     * @return response object (must be closed by caller)
     *
     * @throws IOException on I/O error
     * @throws HttpException on HTTP error
     */
    @Throws(IOException::class, HttpException::class)
    fun mkCol(xmlBody: String?): DavResponse {
        val rqBody = if (xmlBody != null) RequestBody.create(MIME_XML, xmlBody) else null

        var response: Response? = null
        for (attempt in 1..MAX_REDIRECTS) {
            response = httpClient.newCall(Request.Builder()
                    .method("MKCOL", rqBody)
                    .url(location)
                    .build()).execute()
            if (response.isRedirect)
                processRedirect(response)
            else
                break
        }
        checkStatus(response!!)

        return DavResponse.Builder(location)
                .responseBody(response.body())
                .build()
    }

    /**
     * Sends a GET request to the resource. Sends `Accept-Encoding: identity` to disable
     * compression, because compression might change the ETag.
     *
     * Follows up to [MAX_REDIRECTS] redirects.
     *
     * When the server returns ETag and/or Content-Type, they're stored as response properties.
     *
     * @param accept value of Accept header (must not be null, but may be *&#47;*)
     *
     * @return response object (must be closed by caller)
     *
     * @throws IOException on I/O error
     * @throws HttpException on HTTP error
     */
    @Throws(IOException::class, HttpException::class)
    fun get(accept: String): DavResponse {
        var response: Response? = null
        for (attempt in 1..MAX_REDIRECTS) {
            response = httpClient.newCall(Request.Builder()
                    .get()
                    .url(location)
                    .header("Accept", accept)
                    .header("Accept-Encoding", "identity")    // disable compression because it can change the ETag
                    .build()).execute()
            if (response.isRedirect)
                processRedirect(response)
            else
                break
        }
        checkStatus(response!!)

        val properties = mutableListOf<Property>()
        response.header("ETag")?.let { eTag ->
            properties += GetETag(eTag)
        }

        val body = response.body() ?: throw DavException("Received GET response without body", httpResponse = response)
        body.contentType()?.let { mimeType ->
            properties += GetContentType(mimeType)
        }

        return DavResponse.Builder(location)
                .responseBody(body)
                .properties(properties)
                .build()
    }

    /**
     * Sends a PUT request to the resource. Follows up to [MAX_REDIRECTS] redirects.
     *
     * When the server returns an ETag, it is stored in response properties.
     *
     * @param body          new resource body to upload
     * @param ifMatchETag   value of `If-Match` header to set, or null to omit
     * @param ifNoneMatch   indicates whether `If-None-Match: *` ("don't overwrite anything existing") header shall be sent
     *
     * @return response object (must be closed by caller)
     *
     * @throws IOException on I/O error
     * @throws HttpException on HTTP error
     */
    @Throws(IOException::class, HttpException::class)
    fun put(body: RequestBody, ifMatchETag: String?, ifNoneMatch: Boolean): DavResponse {
        var response: Response? = null
        for (attempt in 1..MAX_REDIRECTS) {
            val builder = Request.Builder()
                    .put(body)
                    .url(location)

            if (ifMatchETag != null)
                // only overwrite specific version
                builder.header("If-Match", QuotedStringUtils.asQuotedString(ifMatchETag))
            if (ifNoneMatch)
                // don't overwrite anything existing
                builder.header("If-None-Match", "*")

            response = httpClient.newCall(builder.build()).execute()
            if (response.isRedirect)
                processRedirect(response)
            else
                break
        }
        checkStatus(response!!)

        val properties = mutableListOf<Property>()
        response.header("ETag")?.let { eTag ->
            properties += GetETag(eTag)
        }

        return DavResponse.Builder(location)
                .properties(properties)
                .responseBody(response.body())
                .build()
    }

    /**
     * Sends a DELETE request to the resource. Warning: Sending this request to a collection will
     * delete the collection with all its contents!
     *
     * Follows up to [MAX_REDIRECTS] redirects.
     *
     * @param ifMatchETag value of `If-Match` header to set, or null to omit
     *
     * @return response object (must be closed by caller)
     *
     * @throws IOException on I/O error
     * @throws HttpException on HTTP errors, or when 207 Multi-Status is returned
     *         (because then there was probably a problem with a member resource)
     */
    @Throws(IOException::class, HttpException::class)
    fun delete(ifMatchETag: String?): DavResponse {
        var response: Response? = null
        for (attempt in 1..MAX_REDIRECTS) {
            val builder = Request.Builder()
                    .delete()
                    .url(location)
            if (ifMatchETag != null)
                builder.header("If-Match", QuotedStringUtils.asQuotedString(ifMatchETag))

            response = httpClient.newCall(builder.build()).execute()
            if (response.isRedirect)
                processRedirect(response)
            else
                break
        }

        checkStatus(response!!)
        if (response.code() == 207)
            /* If an error occurs deleting a member resource (a resource other than
               the resource identified in the Request-URI), then the response can be
               a 207 (Multi-Status). […] (RFC 4918 9.6.1. DELETE for Collections) */
            throw HttpException(response)

        return DavResponse.Builder(location)
                .responseBody(response.body())
                .build()
    }

    /**
     * Sends a PROPFIND request to the resource. Expects and processes a 207 Multi-Status response.
     *
     * Follows up to [MAX_REDIRECTS] redirects.
     *
     * @param depth    "Depth" header to send (-1 for `infinity`)
     * @param reqProp  properties to request
     *
     * @return response object (must be closed by caller)
     *
     * @throws IOException on I/O error
     * @throws HttpException on HTTP error
     * @throws DavException on WebDAV error (like no 207 Multi-Status response)
     */
    @Throws(IOException::class, HttpException::class, DavException::class)
    fun propfind(depth: Int, vararg reqProp: Property.Name): DavResponse {
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

        var response: Response? = null
        for (attempt in 1..MAX_REDIRECTS) {
            response = httpClient.newCall(Request.Builder()
                    .url(location)
                    .method("PROPFIND", RequestBody.create(MIME_XML, writer.toString()))
                    .header("Depth", if (depth >= 0) depth.toString() else "infinity")
                    .build()).execute()
            if (response.isRedirect)
                processRedirect(response)
            else
                break
        }

        checkStatus(response!!)
        assertMultiStatus(response)

        return processMultiStatus(response.body()?.charStream()!!)
    }


    // status handling

    /**
     * Checks the status from an HTTP response and throws an exception in case of an error.
     *
     * @throws HttpException in case of an HTTP error
     */
    protected fun checkStatus(response: Response) =
        checkStatus(response.code(), response.message(), response)

    /**
     * Checks the status from an HTTP [StatusLine] and throws an exception in case of an error.
     *
     * @throws HttpException in case of an HTTP error
     */
    protected fun checkStatus(status: StatusLine) =
        checkStatus(status.code, status.message, null)

    /**
     * Checks the status from an HTTP response and throws an exception in case of an error.
     *
     * @throws HttpException in case of an HTTP error
     */
    protected fun checkStatus(code: Int, message: String?, response: Response?) {
        if (code/100 == 2)
            // everything OK
            return

        throw when (code) {
            HttpURLConnection.HTTP_UNAUTHORIZED ->
                if (response != null) UnauthorizedException(response) else UnauthorizedException(message)
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
     * Asserts a 207 Multi-Status response.
     *
     * @throws DavException if the response is not a Multi-Status response
     */
    protected fun assertMultiStatus(response: Response) {
        if (response.code() != 207)
            throw DavException("Expected 207 Multi-Status, got ${response.code()} ${response.message()}")

        if (response.body() == null)
            throw DavException("Received 207 Multi-Status without body")

        response.body()?.contentType()?.let {
            if (((it.type() != "application" && it.type() != "text")) || it.subtype() != "xml")
                throw DavException("Received non-XML 207 Multi-Status")
        } ?: log.warning("Received 207 Multi-Status without Content-Type, assuming XML")
    }

    /**
     * Sets the new [location] in case of a redirect. Closes the [response] body.
     *
     * @throws HttpException when the redirect doesn't have a destination
     */
    protected fun processRedirect(response: Response) {
        try {
            response.header("Location")?.let {
                val target = location.resolve(it)
                if (target != null) {
                    log.fine("Redirected, new location = $target")
                    location = target
                } else
                    throw DavException("Redirected without new Location")
            }
        } finally {
            response.body()?.close()
        }
    }


    // Multi-Status handling

    /**
     * Processes a 207 Multi-Status response.
     *
     * @return response object with properties, members etc.
     *
     * @throws IOException on I/O error
     * @throws HttpException on HTTP error
     * @throws DavException on WebDAV error
     */
    protected fun processMultiStatus(reader: Reader): DavResponse {
        val parser = XmlUtils.newPullParser()

        // some parsing sub-functions
        fun parseMultiStatusProp(): List<Property> {
            // <!ELEMENT prop ANY >
            val depth = parser.depth
            val prop = mutableListOf<Property>()

            var eventType = parser.eventType
            while (!(eventType == XmlPullParser.END_TAG && parser.depth == depth)) {
                if (eventType == XmlPullParser.START_TAG && parser.depth == depth + 1) {
                    val name = Property.Name(parser.namespace, parser.name)
                    val property = PropertyRegistry.create(name, parser)
                    if (property != null)
                        prop += property
                    else
                        log.fine("Ignoring unknown property $name")
                }
                eventType = parser.next()
            }

            return prop.toList()
        }

        fun parseMultiStatusPropStat(): List<Property> {
            // <!ELEMENT propstat (prop, status, error?, responsedescription?) >
            val depth = parser.depth

            var status: StatusLine? = null
            val prop = mutableListOf<Property>()

            var eventType = parser.eventType
            while (!(eventType == XmlPullParser.END_TAG && parser.depth == depth)) {
                if (eventType == XmlPullParser.START_TAG && parser.depth == depth+1)
                    if (parser.namespace == XmlUtils.NS_WEBDAV)
                        when (parser.name) {
                            "prop" ->
                                prop.addAll(parseMultiStatusProp())
                            "status" ->
                                status = try {
                                    StatusLine.parse(parser.nextText())
                                } catch(e: ProtocolException) {
                                    log.warning("Invalid status line, treating as 500 Server Error")
                                    StatusLine(Protocol.HTTP_1_1, 500, "Invalid status line")
                                }
                        }
                eventType = parser.next()
            }

            if (status != null && status.code/100 != 2)
                // not successful, ignore these properties
                prop.clear()

            return prop
        }

        fun parseMultiStatusResponse(root: DavResponse.Builder) {
            /* <!ELEMENT response (href, ((href*, status)|(propstat+)),
                                           error?, responsedescription? , location?) > */
            val depth = parser.depth

            var href: HttpUrl? = null
            var status: StatusLine? = null
            val properties = mutableListOf<Property>()

            var eventType = parser.eventType
            while (!(eventType == XmlPullParser.END_TAG && parser.depth == depth)) {
                if (eventType == XmlPullParser.START_TAG && parser.depth == depth+1)
                    if (parser.namespace == XmlUtils.NS_WEBDAV)
                        when (parser.name) {
                            "href" -> {
                                var sHref = parser.nextText()
                                if (!sHref.startsWith("/")) {
                                    /* According to RFC 4918 8.3 URL Handling, only absolute paths are allowed as relative
                                       URLs. However, some servers reply with relative paths. */
                                    val firstColon = sHref.indexOf(':')
                                    if (firstColon != -1) {
                                        /* There are some servers which return not only relative paths, but relative paths like "a:b.vcf",
                                           which would be interpreted as scheme: "a", scheme-specific part: "b.vcf" normally.
                                           For maximum compatibility, we prefix all relative paths which contain ":" (but not "://"),
                                           with "./" to allow resolving by HttpUrl. */
                                        var hierarchical = false
                                        try {
                                            if (sHref.substring(firstColon, firstColon + 3) == "://")
                                                hierarchical = true
                                        } catch (e: IndexOutOfBoundsException) {
                                            // no "://"
                                        }
                                        if (!hierarchical)
                                            sHref = "./$sHref"
                                    }
                                }
                                href = location.resolve(sHref)
                            }
                            "status" ->
                                status = try {
                                    StatusLine.parse(parser.nextText())
                                } catch(e: ProtocolException) {
                                    log.warning("Invalid status line, treating as 500 Server Error")
                                    StatusLine(Protocol.HTTP_1_1, 500, "Invalid status line")
                                }
                            "propstat" ->
                                properties.addAll(parseMultiStatusPropStat())
                            "location" ->
                                throw DavException("Redirected child resources are not supported yet")
                        }
                eventType = parser.next()
            }

            if (href == null) {
                log.warning("Ignoring <response> without valid <href>")
                return
            }

            // if we know this resource is a collection, make sure href has a trailing slash (for clarity and resolving relative paths)
            val type = properties.filterIsInstance(ResourceType::class.java).firstOrNull()
            if (type != null && type.types.contains(ResourceType.COLLECTION))
                href = UrlUtils.withTrailingSlash(href)

            log.log(Level.FINE, "Received properties for $href", if (status != null) status else properties)

            var removed = false
            var insufficientStorage = false
            status?.let {
                /* Treat an HTTP error of a single response (i.e. requested resource or a member)
                   like an HTTP error of the requested resource.

                Exceptions for RFC 6578 support:
                  - members with 404 Not Found go into removedMembers instead of members
                  - 507 Insufficient Storage on the requested resource can mean there are further results
                */
                when (it.code) {
                    404  -> removed = true
                    507  -> insufficientStorage = true
                    else -> checkStatus(it)
                }
            }

            // Which resource does this <response> represent?
            var target: DavResponse.Builder? = null
            if (UrlUtils.equals(UrlUtils.omitTrailingSlash(href), UrlUtils.omitTrailingSlash(location)) && !removed) {
                // it's about ourselves (and not 404)
                target = root

                if (insufficientStorage)
                    target.furtherResults(true)

            } else if (location.scheme() == href.scheme() && location.host() == href.host() && location.port() == href.port()) {
                val locationSegments = location.pathSegments()
                val hrefSegments = href.pathSegments()

                // don't compare trailing slash segment ("")
                var nBasePathSegments = locationSegments.size
                if (locationSegments[nBasePathSegments-1] == "")
                    nBasePathSegments--

                /* example:   locationSegments  = [ "davCollection", "" ]
                              nBasePathSegments = 1
                              hrefSegments      = [ "davCollection", "aMember" ]
                */
                if (hrefSegments.size > nBasePathSegments) {
                    val sameBasePath = (0 until nBasePathSegments).none { locationSegments[it] != hrefSegments[it] }
                    if (sameBasePath) {
                        // it's about a member
                        target = DavResponse.Builder(href)
                        if (!removed)
                            root.addMember(target)
                        else
                            root.addRemovedMember(target)
                    }
                }
            }

            if (target == null && !removed) {
                log.warning("Received <response> not for self and not for member resource: $href")
                target = DavResponse.Builder(href)
                root.addRelated(target)
            }

            // set properties
            target?.properties(properties)
        }

        fun parseMultiStatus(): DavResponse {
            // <!ELEMENT multistatus (response*, responsedescription?)  >
            val builder = DavResponse.Builder(location)

            val depth = parser.depth
            var eventType = parser.eventType
            while (!(eventType == XmlPullParser.END_TAG && parser.depth == depth)) {
                if (eventType == XmlPullParser.START_TAG && parser.depth == depth + 1 && parser.namespace == XmlUtils.NS_WEBDAV)
                    when (parser.name) {
                        "response" ->
                            parseMultiStatusResponse(builder)
                        "sync-token" ->
                            XmlUtils.readText(parser)?.let {
                                builder.syncToken(SyncToken(it))
                            }
                    }
                eventType = parser.next()
            }

            return builder.build()
        }

        try {
            parser.setInput(reader)

            var response: DavResponse? = null
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.depth == 1)
                    if (parser.namespace == XmlUtils.NS_WEBDAV && parser.name == "multistatus") {
                        response = parseMultiStatus()
                        // ignore further <multistatus> elements
                        break
                    }
                eventType = parser.next()
            }

            return response ?: throw DavException("Multi-Status response didn't contain <DAV:multistatus> root element")

        } catch (e: EOFException) {
            throw DavException("Incomplete Multi-Status XML", e)
        } catch (e: XmlPullParserException) {
            throw DavException("Couldn't parse Multi-Status XML", e)
        }
    }

}
