/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.dav4jvm

import at.bitfire.dav4jvm.Constants.log
import at.bitfire.dav4jvm.property.ResourceType
import okhttp3.HttpUrl
import okhttp3.Protocol
import okhttp3.internal.http.StatusLine
import org.xmlpull.v1.XmlPullParser
import java.net.ProtocolException

/**
 * Represents a WebDAV response XML Element.
 *
 *     <!ELEMENT response (href, ((href*, status)|(propstat+)),
 *                         error?, responsedescription? , location?) >
 */
data class Response(
        /**
         * URL of the requested resource. For instance, if `this` is a result
         * of a PROPFIND request, the `requestedUrl` would be the URL where the
         * PROPFIND request has been sent to (usually the collection URL).
         */
        val requestedUrl: HttpUrl,

        /**
         * URL of this response (`href` element)
         */
        val href: HttpUrl,

        /**
         * status of this response (`status` XML element)
         */
        val status: StatusLine?,

        /**
         * property/status elements (`propstat` XML elements)
         */
        val propstat: List<PropStat>,

        /**
         * list of precondition/postcondition elements (`error` XML elements)
         */
        val error: List<Error>? = null,

        /**
         * new location of this response (`location` XML element), used for redirects
         */
        val newLocation: HttpUrl? = null
) {

    enum class HrefRelation {
        SELF, MEMBER, OTHER
    }

    /**
     * All properties from propstat elements with empty status or status code 2xx.
     */
    val properties: List<Property> by lazy {
        if (isSuccess())
            propstat.filter { it.isSuccess() }.map { it.properties }.flatten()
        else
            emptyList()
    }

    /**
     * Convenience method to get a certain property with empty status or status code 2xx
     * from the current response.
     */
    operator fun<T: Property> get(clazz: Class<T>) =
            properties.filterIsInstance(clazz).firstOrNull()

    /**
     * Returns whether the request was successful.
     *
     * @return true: no status XML element or status code 2xx; false: otherwise
     */
    fun isSuccess() = status == null || status.code/100 == 2

    /**
     * Returns the name (last path segment) of the resource.
     */
    fun hrefName() = HttpUtils.fileName(href)


    companion object {

        /**
         * Parses an XML response element.
         */
        fun parse(parser: XmlPullParser, location: HttpUrl, callback: DavResponseCallback) {
            val depth = parser.depth

            var href: HttpUrl? = null
            var status: StatusLine? = null
            val propStat = mutableListOf<PropStat>()
            var error: List<Error>? = null
            var newLocation: HttpUrl? = null

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
                                    log.warning("Invalid status line, treating as HTTP error 500")
                                    StatusLine(Protocol.HTTP_1_1, 500, "Invalid status line")
                                }
                            "propstat" ->
                                PropStat.parse(parser).let { propStat += it }
                            "error" ->
                                error = Error.parseError(parser)
                            "location" ->
                                newLocation = HttpUrl.parse(parser.nextText())
                        }
                eventType = parser.next()
            }

            if (href == null) {
                log.warning("Ignoring XML response element without valid href")
                return
            }

            // if we know this resource is a collection, make sure href has a trailing slash
            // (for clarity and resolving relative paths)
            propStat.filter { it.isSuccess() }
                    .map { it.properties }
                    .filterIsInstance(ResourceType::class.java)
                    .firstOrNull()
                    ?.let { type ->
                        if (type.types.contains(ResourceType.COLLECTION))
                            href = UrlUtils.withTrailingSlash(href!!)
                    }

            //log.log(Level.FINE, "Received properties for $href", if (status != null) status else propStat)

            // Which resource does this <response> represent?
            val relation = when {
                UrlUtils.equals(UrlUtils.omitTrailingSlash(href!!), UrlUtils.omitTrailingSlash(location)) ->
                    HrefRelation.SELF
                else -> {
                    if (location.scheme() == href!!.scheme() && location.host() == href!!.host() && location.port() == href!!.port()) {
                        val locationSegments = location.pathSegments()
                        val hrefSegments = href!!.pathSegments()

                        // don't compare trailing slash segment ("")
                        var nBasePathSegments = locationSegments.size
                        if (locationSegments[nBasePathSegments-1] == "")
                            nBasePathSegments--

                        /* example:   locationSegments  = [ "davCollection", "" ]
                                      nBasePathSegments = 1
                                      hrefSegments      = [ "davCollection", "aMember" ]
                        */
                        var relation = HrefRelation.OTHER
                        if (hrefSegments.size > nBasePathSegments) {
                            val sameBasePath = (0 until nBasePathSegments).none { locationSegments[it] != hrefSegments[it] }
                            if (sameBasePath)
                                relation = HrefRelation.MEMBER
                        }

                        relation
                    } else
                        HrefRelation.OTHER
                }
            }

            callback(
                    Response(
                            location,
                            href!!,
                            status,
                            propStat,
                            error,
                            newLocation
                    ),
                    relation)
        }

    }

}

typealias DavResponseCallback = (response: Response, relation: Response.HrefRelation) -> Unit
