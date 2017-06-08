/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.dav4android

import android.text.TextUtils
import at.bitfire.dav4android.exception.*
import at.bitfire.dav4android.property.GetContentType
import at.bitfire.dav4android.property.GetETag
import at.bitfire.dav4android.property.ResourceType
import okhttp3.*
import okhttp3.internal.http.StatusLine
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.io.Reader
import java.io.StringWriter
import java.net.HttpURLConnection
import java.net.ProtocolException
import java.util.*
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Represents a WebDAV resource at the given location.
 * @param httpClient    #{@link OkHttpClient} to access this object
 * @param location      location of the WebDAV resource
 * @param log           #{@link Logger} which will be used for logging, or null for default
 */
open class DavResource @JvmOverloads constructor(
        val httpClient: OkHttpClient,
        var location: HttpUrl,
        val log: Logger = Constants.log
) {

    val MIME_XML = MediaType.parse("application/xml; charset=utf-8")

    val MAX_REDIRECTS = 5

    val capabilities = HashSet<String>()
    val properties = PropertyCollection()
    val members = HashSet<DavResource>()
    val related = HashSet<DavResource>()

    val registry = PropertyRegistry


    init {
        // Don't follow redirects (only useful for GET/POST).
        // This means we have to handle 30x responses manually.
        if (httpClient.followRedirects())
            throw IllegalArgumentException("httpClient must not follow redirects automatically")
    }


    fun fileName(): String {
        val pathSegments = location.pathSegments()
        return pathSegments[pathSegments.size - 1]
    }

    override fun toString() = location.toString()


    /**
     * Sends an OPTIONS request to this resource, requesting [capabilities].
     * @throws IOException on I/O error
     * @throws HttpException on HTTP error
     * @throws DavException on DAV error
     */
    @Throws(IOException::class, HttpException::class, DavException::class)
    fun options() {
        capabilities.clear()

        val response = httpClient.newCall(Request.Builder()
                .method("OPTIONS", null)
                .header("Content-Length", "0")      // workaround for https://github.com/square/okhttp/issues/2892
                .url(location)
                .build()).execute()
        checkStatus(response, true)

        HttpUtils.listHeader(response, "DAV").mapTo(capabilities) { it.trim() }
    }

    /**
     * Sends a MKCOL request to this resource.
     * @throws IOException on I/O error
     * @throws HttpException on HTTP error
     */
    @Throws(IOException::class, HttpException::class)
    fun mkCol(xmlBody: String?) {
        val rqBody = if (xmlBody != null) RequestBody.create(MIME_XML, xmlBody) else null

        var response: Response? = null
        for (attempt in 0..MAX_REDIRECTS-1) {
            response = httpClient.newCall(Request.Builder()
                    .method("MKCOL", rqBody)
                    .url(location)
                    .build()).execute()
            if (response.isRedirect)
                processRedirection(response)
            else
                break
        }
        checkStatus(response!!, true)
    }

    /**
     * Sends a GET request to the resource. Note that this method expects the server to
     * return an ETag (which is required for CalDAV and CardDAV, but not for WebDAV in general).
     * @param accept    content of Accept header (must not be null, but may be &#42;&#47;* )
     * @return          response body (has to be closed by caller)
     * @throws IOException on I/O error
     * @throws HttpException on HTTP error
     * @throws DavException on WebDAV error, or when the response doesn't contain an ETag
     */
    @Throws(IOException::class, HttpException::class, DavException::class)
    fun get(accept: String): ResponseBody {
        var response: Response? = null
        for (attempt in 0..MAX_REDIRECTS-1) {
            response = httpClient.newCall(Request.Builder()
                    .get()
                    .url(location)
                    .header("Accept", accept)
                    .header("Accept-Encoding", "identity")    // disable compression because it can change the ETag
                    .build()).execute()
            if (response.isRedirect)
                processRedirection(response)
            else
                break
        }
        checkStatus(response!!, false)

        val eTag = response.header("ETag")
        if (TextUtils.isEmpty(eTag))
            properties.remove(GetETag.NAME)
        else
            properties[GetETag.NAME] = GetETag(eTag)

        val body = response.body() ?: throw HttpException("GET without response body")

        val mimeType = body.contentType()
        if (mimeType != null)
            properties[GetContentType.NAME] = GetContentType(mimeType)

        return body
    }

    /**
     * Sends a PUT request to the resource.
     * @param body              new resource body to upload
     * @param ifMatchETag       value of "If-Match" header to set, or null to omit
     * @param ifNoneMatch       indicates whether "If-None-Match: *" ("don't overwrite anything existing") header shall be sent
     * @return                  true if the request was redirected successfully, i.e. #{@link #location} and maybe resource name may have changed
     * @throws IOException on I/O error
     * @throws HttpException on HTTP error
     */
    @Throws(IOException::class, HttpException::class)
    fun put(body: RequestBody, ifMatchETag: String?, ifNoneMatch: Boolean): Boolean {
        var redirected = false
        var response: Response? = null
        for (attempt in 0..MAX_REDIRECTS-1) {
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
            if (response.isRedirect) {
                processRedirection(response)
                redirected = true
            } else
                break
        }
        checkStatus(response!!, true)

        val eTag = response.header("ETag")
        if (TextUtils.isEmpty(eTag))
            properties.remove(GetETag.NAME)
        else
            properties[GetETag.NAME] = GetETag(eTag)

        return redirected
    }

    /**
     * Sends a DELETE request to the resource.
     * @param ifMatchETag value of "If-Match" header to set, or null to omit
     * @throws IOException on I/O error
     * @throws HttpException on HTTP errors, including redirects
     */
    @Throws(IOException::class, HttpException::class)
    fun delete(ifMatchETag: String?) {
        var response: Response? = null
        for (attempt in 0..MAX_REDIRECTS-1) {
            val builder = Request.Builder()
                    .delete()
                    .url(location)
            if (ifMatchETag != null)
                builder.header("If-Match", QuotedStringUtils.asQuotedString(ifMatchETag))

            response = httpClient.newCall(builder.build()).execute()
            if (response.isRedirect) {
                processRedirection(response)
            } else
                break
        }

        checkStatus(response!!, false)
        if (response.code() == 207) {
            /* If an error occurs deleting a member resource (a resource other than
               the resource identified in the Request-URI), then the response can be
               a 207 (Multi-Status). […] (RFC 4918 9.6.1. DELETE for Collections) */
            throw HttpException(response)
        } else
            response.body()?.close()
    }

    /**
     * Sends a PROPFIND request to the resource. Expects and processes a 207 multi-status response.
     * #{@link #properties} are updated according to the multi-status response.
     * #{@link #members} is re-built according to the multi-status response (i.e. previous member entries won't be retained).
     * @param depth      "Depth" header to send, e.g. 0 or 1
     * @param reqProp    properties to request
     * @throws IOException on I/O error
     * @throws HttpException on HTTP error
     * @throws DavException on WebDAV error
     */
    @Throws(IOException::class, HttpException::class, DavException::class)
    fun propfind(depth: Int, vararg reqProp: Property.Name) {
        // build XML request body
        val serializer = XmlUtils.newSerializer()
        val writer = StringWriter()
        serializer.setOutput(writer)
        serializer.setPrefix("", XmlUtils.NS_WEBDAV)
        serializer.setPrefix("CAL", XmlUtils.NS_CALDAV)
        serializer.setPrefix("CARD", XmlUtils.NS_CARDDAV)
        serializer.startDocument("UTF-8", null)
        serializer.setPrefix("", XmlUtils.NS_WEBDAV)
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
        for (attempt in 0..MAX_REDIRECTS-1) {
            response = httpClient.newCall(Request.Builder()
                    .url(location)
                    .method("PROPFIND", RequestBody.create(MIME_XML, writer.toString()))
                    .header("Depth", depth.toString())
                    .build()).execute()
            if (response.isRedirect)
                processRedirection(response)
            else
                break
        }

        checkStatus(response!!, false)
        assertMultiStatus(response)

        if (depth > 0)
            // collection listing requested, drop old member information
            members.clear()

        response.body()?.charStream()?.use { processMultiStatus(it) }
    }


    // status handling

    /**
     * Checks the status from an HTTP response and throws an exception in case of an error.
     * @param closeBody whether [response] shall be closed by this method
     * @throws HttpException in case of an HTTP error
     */
    protected fun checkStatus(response: Response, closeBody: Boolean) {
        checkStatus(response.code(), response.message(), response)

        if (closeBody)
            response.body()?.close()
    }

    /**
     * Checks the status from an HTTP [StatusLine] and throws an exception in case of an error.
     * @throws HttpException in case of an HTTP error
     */
    protected fun checkStatus(status: StatusLine) =
        checkStatus(status.code, status.message, null)

    /**
     * Checks the status from an HTTP response and throws an exception in case of an error.
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
     * Asserts a 207 multi-status response.
     * @throws DavException if the response is not a multi-status response with body
     */
    protected fun assertMultiStatus(response: Response) {
        if (response.code() != 207)
            throw InvalidDavResponseException("Expected 207 Multi-Status, got ${response.code()} ${response.message()}")

        if (response.body() == null)
            throw InvalidDavResponseException("Received 207 Multi-Status without body")

        val mediaType = response.body()?.contentType()
        if (mediaType != null) {
            if (((mediaType.type() != "application" && mediaType.type() != "text")) || mediaType.subtype() != "xml")
                throw InvalidDavResponseException("Received non-XML 207 Multi-Status")
        } else
            log.warning("Received 207 Multi-Status without Content-Type, assuming XML")
    }

    /**
     * @throws HttpException in case of an HTTP error
     */
    protected fun processRedirection(response: Response) {
        try {
            response.header("Location")?.let {
                val target = location.resolve(it)
                if (target != null) {
                    log.fine("Redirected, new location = $target")
                    location = target
                } else
                    throw HttpException("Redirected without new Location")
            }
        } finally {
            response.body()?.close()
        }
    }


    // multi-status handling

    /**
     * Process a 207 multi-status response.
     * @throws IOException on I/O error
     * @throws HttpException on HTTP error
     * @throws DavException on WebDAV error
     */
    protected fun processMultiStatus(reader: Reader) {
        val parser = XmlUtils.newPullParser()

        // some parsing sub-functions
        fun parseMultiStatus_Prop(): PropertyCollection? {
            // <!ELEMENT prop ANY >
            val depth = parser.depth
            val prop = PropertyCollection()

            var eventType = parser.eventType
            while (!(eventType == XmlPullParser.END_TAG && parser.depth == depth)) {
                if (eventType == XmlPullParser.START_TAG && parser.depth == depth+1) {
                    val name = Property.Name(parser.namespace, parser.name)
                    val property = registry.create(name, parser)
                    if (property != null)
                        prop[name] = property
                    else
                        log.fine("Ignoring unknown property $name")
                }
                eventType = parser.next()
            }

            return prop
        }

        fun parseMultiStatus_PropStat(): PropertyCollection? {
            // <!ELEMENT propstat (prop, status, error?, responsedescription?) >
            val depth = parser.depth

            var status: StatusLine? = null
            var prop: PropertyCollection? = null

            var eventType = parser.eventType
            while (!(eventType == XmlPullParser.END_TAG && parser.depth == depth)) {
                if (eventType == XmlPullParser.START_TAG && parser.depth == depth+1)
                    if (parser.namespace == XmlUtils.NS_WEBDAV)
                        when (parser.name) {
                            "prop" ->
                                prop = parseMultiStatus_Prop()
                            "status" ->
                                try {
                                    status = StatusLine.parse(parser.nextText())
                                } catch(e: ProtocolException) {
                                    log.warning("Invalid status line, treating as 500 Server Error")
                                    status = StatusLine(Protocol.HTTP_1_1, 500, "Invalid status line")
                                }
                        }
                eventType = parser.next()
            }

            if (prop != null && status != null && status.code/100 != 2)
                // not successful, null out property values so that they can be removed when merging in parseMultiStatus_Response
                prop.nullAllValues()

            return prop
        }

        fun parseMultiStatus_Response() {
            /* <!ELEMENT response (href, ((href*, status)|(propstat+)),
                                           error?, responsedescription? , location?) > */
            val depth = parser.depth

            var href: HttpUrl? = null
            var status: StatusLine? = null
            val properties = PropertyCollection()

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
                                try {
                                    status = StatusLine.parse(parser.nextText())
                                } catch(e: ProtocolException) {
                                    log.warning("Invalid status line, treating as 500 Server Error")
                                    status = StatusLine(Protocol.HTTP_1_1, 500, "Invalid status line")
                                }
                            "propstat" ->
                                parseMultiStatus_PropStat()?.let { properties.merge(it, false) }
                            "location" ->
                                throw UnsupportedDavException("Redirected child resources are not supported yet")
                        }
                eventType = parser.next()
            }

            if (href == null) {
                log.warning("Ignoring <response> without valid <href>")
                return
            }

            // if we know this resource is a collection, make sure href has a trailing slash (for clarity and resolving relative paths)
            val type = properties[ResourceType.NAME] as ResourceType?
            if (type != null && type.types.contains(ResourceType.COLLECTION))
                href = UrlUtils.withTrailingSlash(href)

            log.log(Level.FINE, "Received <response> for $href", if (status != null) status else properties)

            if (status != null)
                // treat an HTTP error of a single response (i.e. requested resource or a member) like an HTTP error of the requested resource
                checkStatus(status!!)

            // Which resource does this <response> represent?
            var target: DavResource? = null
            if (UrlUtils.equals(UrlUtils.omitTrailingSlash(href), UrlUtils.omitTrailingSlash(location))) {
                // it's about ourselves
                target = this
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
                    val sameBasePath = (0..nBasePathSegments-1).none { locationSegments[it] != hrefSegments[it] }
                    if (sameBasePath) {
                        target = DavResource(httpClient, href, log)
                        members.add(target)
                    }
                }
            }

            if (target == null) {
                log.warning("Received <response> not for self and not for member resource: $href")
                target = DavResource(httpClient, href, log)
                related.add(target)
            }

            // set properties for target
            target.properties.merge(properties, true)
        }

        fun parseMultiStatus() {
            // <!ELEMENT multistatus (response*, responsedescription?)  >
            val depth = parser.depth

            var eventType = parser.eventType
            while (!(eventType == XmlPullParser.END_TAG && parser.depth == depth)) {
                if (eventType == XmlPullParser.START_TAG && parser.depth == depth+1 &&
                        parser.namespace == XmlUtils.NS_WEBDAV && parser.name == "response")
                    parseMultiStatus_Response()
                eventType = parser.next()
            }
        }

        try {
            parser.setInput(reader)

            var multiStatus = false

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.depth == 1)
                    if (parser.namespace == XmlUtils.NS_WEBDAV && parser.name == "multistatus") {
                        parseMultiStatus()
                        multiStatus = true
                    }
                eventType = parser.next()
            }

            if (!multiStatus)
                throw InvalidDavResponseException("Multi-Status response didn't contain <DAV:multistatus> root element")

        } catch (e: XmlPullParserException) {
            throw InvalidDavResponseException("Couldn't parse Multi-Status XML", e)
        }
    }


    // helpers

    /** Finds first property within all responses (including unasked responses) */
    fun findProperty(name: Property.Name): Pair<DavResource, Property>? {
        // check resource itself
        val property = properties[name]
        if (property != null)
            return Pair<DavResource, Property>(this, property)

        // check members
        for (member in members)
            member.findProperty(name)?.let { return it }

        // check unrequested responses
        for (resource in related)
            resource.findProperty(name)?.let { return it }

        return null
    }

    /** Finds properties within all responses (including unasked responses) */
    fun findProperties(name: Property.Name): List<Pair<DavResource, Property>> {
        val result = LinkedList<Pair<DavResource, Property>>()

        // check resource itself
        val property = properties[name]
        if (property != null)
            result.add(Pair<DavResource, Property>(this, property))

        // check members
        for (member in members)
            result.addAll(member.findProperties(name))

        // check unrequested responses
        for (rel in related)
            result.addAll(rel.findProperties(name))

        return Collections.unmodifiableList(result)
    }

}
