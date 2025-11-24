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
import at.bitfire.dav4jvm.XmlUtils.propertyName
import at.bitfire.dav4jvm.ktor.Response.HrefRelation
import at.bitfire.dav4jvm.property.webdav.ResourceType
import at.bitfire.dav4jvm.property.webdav.WebDAV
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.URLParserException
import io.ktor.http.Url
import io.ktor.http.isSuccess
import io.ktor.http.takeFrom
import org.jetbrains.annotations.VisibleForTesting
import org.xmlpull.v1.XmlPullParser
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Parses a `<response>` XML element of a multistatus response.
 *
 * @param location  location of the request (used to resolve possible relative `<href>`)
 */
class ResponseParser(
    private val location: Url,
    private val callback: MultiResponseCallback
) {

    private val logger
        get() = Logger.getLogger(javaClass.name)

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
    suspend fun parseResponse(parser: XmlPullParser) {
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
                    WebDAV.Href ->
                        hrefOrNull = resolveHref(parser.nextText())
                    WebDAV.Status ->
                        status = KtorHttpUtils.parseStatusLine(parser.nextText())
                    WebDAV.PropStat ->
                        PropStatParser.parse(parser).let { propStat += it }
                    WebDAV.Error ->
                        error = Error.parseError(parser)
                    WebDAV.Location ->
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
            .flatMap { it.properties }
            .filterIsInstance<ResourceType>()
            .firstOrNull()
            ?.let { type ->
                if (type.types.contains(WebDAV.Collection))
                    href = UrlUtils.withTrailingSlash(href)
            }

        //log.log(Level.FINE, "Received properties for $href", if (status != null) status else propStat)

        // Which resource does this <response> represent?
        val relation = when {
            UrlUtils.omitTrailingSlash(href).equalsForWebDAV(UrlUtils.omitTrailingSlash(location)) ->
                HrefRelation.SELF

            else -> {
                if (location.protocol.name == href.protocol.name && location.host == href.host && location.port == href.port) {
                    val locationSegments = location.rawSegments
                    val hrefSegments = href.rawSegments

                    // don't compare trailing slash segment ("")
                    var nBasePathSegments = locationSegments.size
                    if (locationSegments.lastOrNull() == "")
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

    @VisibleForTesting
    internal fun resolveHref(hrefString: String): Url? {
        var sHref = hrefString

        var preserve = false
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
                        preserve = true
                } catch (_: IndexOutOfBoundsException) {
                    // no "://"
                }
                if (!preserve)
                    sHref = "./$sHref"
            }
        }

        val urlBuilder = try {
            URLBuilder(location).takeFrom(sHref)
        } catch (e: URLParserException) {
            logger.log(Level.WARNING, "Unresolvable <href> in <response>: $hrefString", e)
            return null
        }

        if (!preserve)      // drop segments that are "./"
            urlBuilder.pathSegments = urlBuilder.pathSegments.filterNot { it == "." }

        return urlBuilder.build()
    }

}