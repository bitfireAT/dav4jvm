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

import io.ktor.http.URLBuilder
import io.ktor.http.URLParserException
import io.ktor.http.Url
import io.ktor.http.takeFrom

object UrlUtils {

    /**
     * Gets the first-level domain name (without subdomains) from a host name.
     * Also removes trailing dots.
     *
     * @param host name (e.g. `www.example.com.`)
     *
     * @return domain name (e.g. `example.com`)
     */
    fun hostToDomain(host: String?): String? {
        if (host == null)
            return null

        // remove optional dot at end
        val withoutTrailingDot = host.removeSuffix(".")

        // split into labels
        val labels = withoutTrailingDot.split('.')
        return if (labels.size >= 2) {
            labels[labels.size - 2] + "." + labels[labels.size - 1]
        } else
            withoutTrailingDot
    }

}

fun Url.hasTrailingSlash(): Boolean =
    rawSegments.isNotEmpty() && rawSegments.last() == ""

/**
 * Ensures that a given URL doesn't have a trailing slash after member names.
 * If the path is the root path (`/`), the slash is preserved.
 *
 * @return URL without trailing slash (except when the path is the root path), e.g. `http://host/test1`
 */
fun Url.omitTrailingSlash(): Url =
    if (hasTrailingSlash())
        URLBuilder(this).apply { pathSegments = pathSegments.dropLast(1) }.build()
    else
        this

/**
 * Ensures that a given URL has a trailing slash after member names.
 *
 * @return URL with trailing slash, e.g. `http://host/test1/`
 */
fun Url.withTrailingSlash(): Url =
    if (hasTrailingSlash())
        this
    else
        URLBuilder(this).apply { pathSegments += "" }.build()

/**
 * Returns parent URL (parent folder). Always with trailing slash.
 */
fun Url.parent(): Url {
    val segments = this.rawSegments
    if (segments.size <= 1) {
        // root URL, ensure it has trailing slash
        return if (segments.lastOrNull() == "")
            this
        else
            URLBuilder(this).apply { pathSegments = listOf("") }.build()
    }
    return if (segments.last() == "")
        URLBuilder(this).apply { pathSegments = segments.dropLast(2) + "" }.build()
    else
        URLBuilder(this).apply { pathSegments = segments.dropLast(1) + "" }.build()
}

/**
 * Resolves `this` URL against a relative path.
 *
 * See [URLBuilder.takeFrom] for details of resolving.
 *
 * @param relative The relative path to resolve.
 * @return A new [Url] representing the resolved URL.
 */
fun Url.resolve(relative: String): Url? =
    try {
        URLBuilder(this).takeFrom(relative).build()
    } catch (_: URLParserException) {
        // thrown by takeFrom on invalid URL string
        null
    }

/**
 * Compares two [Url]s in WebDAV context. If two URLs are considered *equal*, both
 * represent the same WebDAV resource.
 *
 * The fragment of an URL is ignored, e.g. `http://host:80/folder1` and `http://HOST/folder1#somefragment`
 * are considered to be equal.
 *
 * [Url] is less strict than [java.net.URI] and allows for instance (not encoded) square brackets in the path.
 * So this method compares the protocol, host (case insensitive), port and rawSegments
 * and and returns true if all are the same.
 *
 * Attention: **This method does not deal with trailing slashes**, so if you want to compare collection URLs,
 * make sure they both (don't) have a trailing slash before calling this method, for instance
 * with [omitTrailingSlash] or [withTrailingSlash].
 *
 * @param other the URL to compare the current object with
 *
 * @return whether the URLs are considered to represent the same WebDAV resource
 */
fun Url.equalsForWebDAV(other: Url): Boolean {
    // if Ktor thinks the two URLs are equal, they're in any case
    // (and it's a simple String comparison)
    if (this == other)
        return true

    return this.protocol == other.protocol
            && this.host.lowercase() == other.host.lowercase()
            && this.port == other.port
            && this.rawSegments == other.rawSegments
}

/**
 * Converts the [String] into a Ktor [Url], if possible.
 *
 * Differs from [io.ktor.http.parseUrl]:
 *
 * - `"relative".toUrlOrNull() == Url("relative")` (without host) but
 * - `parseUrl("relative") == null` (requires host)
 *
 * @return the Ktor [Url], or `null` if the string couldn't be converted
 */
fun String?.toUrlOrNull(): Url? {
    if (this == null)
        return null

    return try {
        Url(this)
    } catch (_: Exception) {
        // parseUrl doesn't catch URLDecodeException, see https://github.com/ktorio/ktor/pull/5231
        null
    }
}