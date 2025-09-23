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

import at.bitfire.dav4jvm.Error
import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.XmlUtils.propertyName
import at.bitfire.dav4jvm.property.webdav.NS_WEBDAV
import at.bitfire.dav4jvm.property.webdav.ResourceType
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.isSuccess
import io.ktor.http.takeFrom
import org.xmlpull.v1.XmlPullParser
import java.util.logging.Logger

/**
 * Represents a WebDAV response XML Element.
 *
 *     <!ELEMENT response (href, ((href*, status)|(propstat+)),
 *                         error?, responsedescription? , location?) >
 */
@Suppress("unused")
data class Response(
    /**
     * URL of the requested resource. For instance, if `this` is a result
     * of a PROPFIND request, the `requestedUrl` would be the URL where the
     * PROPFIND request has been sent to (usually the collection URL).
     */
    val requestedUrl: Url,

    /**
     * URL of this response (`href` element)
     */
    val href: Url,

    /**
     * status of this response (`status` XML element)
     */
    val status: HttpStatusCode?,

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
    val newLocation: Url? = null
) {

    enum class HrefRelation {
        SELF, MEMBER, OTHER
    }

    /**
     * All properties from propstat elements with empty status or status code 2xx.
     */
    val properties: List<Property> by lazy {
        if (isSuccess())
            propstat.filter { it.status.isSuccess() }.map { it.properties }.flatten()
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
    fun isSuccess() = status == null || status.isSuccess()

    /**
     * Returns the name (last path segment) of the resource.
     */
    fun hrefName() = KtorHttpUtils.fileName(href)


    companion object {

        val RESPONSE = Property.Name(NS_WEBDAV, "response")
        val MULTISTATUS = Property.Name(NS_WEBDAV, "multistatus")
        val STATUS = Property.Name(NS_WEBDAV, "status")
        val LOCATION = Property.Name(NS_WEBDAV, "location")

        /**
         * Parses an XML response element and calls the [callback] for it (when it has a `<href>`).
         * The arguments of the [MultiResponseCallback.onResponse] are set accordingly.
         *
         * If the [at.bitfire.dav4jvm.property.webdav.ResourceType] of the queried resource is known (= was queried and returned by the server)
         * and it contains [at.bitfire.dav4jvm.property.webdav.ResourceType.Companion.COLLECTION], the `href` property of the callback will automatically
         * have a trailing slash.
         *
         * So if you want PROPFIND results to have a trailing slash when they are collections, make sure
         * that you query [at.bitfire.dav4jvm.property.webdav.ResourceType].
         */
        fun parse(parser: XmlPullParser, location: Url, callback: MultiResponseCallback) {
            val logger = Logger.getLogger(Response::javaClass.name)

            val depth = parser.depth

            var hrefOrNull: Url? = null
            var status: HttpStatusCode? = null
            val propStat = mutableListOf<PropStat>()
            var error: List<Error>? = null
            var newLocation: Url? = null

            var eventType = parser.eventType
            while (!(eventType == XmlPullParser.END_TAG && parser.depth == depth)) {
                if (eventType == XmlPullParser.START_TAG && parser.depth == depth+1)
                    when (parser.propertyName()) {
                        DavResource.Companion.HREF -> {
                            var sHref = parser.nextText()
                            var hierarchical = false
                            if (!sHref.startsWith("/")) {
                                /* According to RFC 4918 8.3 URL Handling, only absolute paths are allowed as relative
                                   URLs. However, some servers reply with relative paths. */
                                val firstColon = sHref.indexOf(':')
                                if (firstColon != -1) {
                                    /* There are some servers which return not only relative paths, but relative paths like "a:b.vcf",
                                       which would be interpreted as scheme: "a", scheme-specific part: "b.vcf" normally.
                                       For maximum compatibility, we prefix all relative paths which contain ":" (but not "://"),
                                       with "./" to allow resolving by HttpUrl. */
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


                            if(!hierarchical) {
                                val urlBuilder = URLBuilder(location).takeFrom(sHref)
                                urlBuilder.pathSegments = urlBuilder.pathSegments.filterNot { it == "." } // Drop segments that are "./"
                                hrefOrNull = urlBuilder.build()
                            } else {
                                hrefOrNull = URLBuilder(location).takeFrom(sHref).build()
                            }
                        }
                        STATUS ->
                            status = KtorHttpUtils.parseStatusLine(parser.nextText())
                        PropStat.Companion.NAME ->
                            PropStat.Companion.parse(parser).let { propStat += it }
                        Error.NAME ->
                            error = Error.parseError(parser)
                        LOCATION ->
                            newLocation = Url(parser.nextText())    // TODO: Need to catch exception here?
                        }
                eventType = parser.next()
            }

            if (hrefOrNull == null) {
                logger.warning("Ignoring XML response element without valid href")
                return
            }
            var href: Url = hrefOrNull      // guaranteed to be not null

            // if we know this resource is a collection, make sure href has a trailing slash
            // (for clarity and resolving relative paths)
            propStat.filter { it.status.isSuccess() }
                .map { it.properties }
                .filterIsInstance<ResourceType>()
                .firstOrNull()
                ?.let { type ->
                    if (type.types.contains(ResourceType.Companion.COLLECTION))
                        href = UrlUtils.withTrailingSlash(href)
                }

            //log.log(Level.FINE, "Received properties for $href", if (status != null) status else propStat)

            // Which resource does this <response> represent?
            val relation = when {
                UrlUtils.omitTrailingSlash(href).equalsForWebDAV(UrlUtils.omitTrailingSlash(location)) ->
                    HrefRelation.SELF

                else -> {
                    if (location.protocol.name == href.protocol.name && location.host == href.host && location.port == href.port) {
                        val locationSegments = location.segments
                        val hrefSegments = href.segments

                        // don't compare trailing slash segment ("")
                        var nBasePathSegments = locationSegments.size
                        if (locationSegments[nBasePathSegments - 1] == "")   // TODO: Ricki, Not sure if this is still needed
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

            callback.onResponse(
                Response(
                    requestedUrl = location,
                    href = href,
                    status = status,
                    propstat = propStat,
                    error = error,
                    newLocation = newLocation
                ),
                relation
            )
        }

    }

}