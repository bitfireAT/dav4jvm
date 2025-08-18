/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

package at.bitfire.dav4jvm.okhttp

import at.bitfire.dav4jvm.okhttp.UrlUtils.omitTrailingSlash
import at.bitfire.dav4jvm.okhttp.UrlUtils.withTrailingSlash
import okhttp3.HttpUrl

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

    /**
     * Ensures that a given URL doesn't have a trailing slash after member names.
     * If the path is the root path (`/`), the slash is preserved.
     *
     * @param url URL to process (e.g. 'http://host/test1/')
     *
     * @return URL without trailing slash (except when the path is the root path), e.g. `http://host/test1`
     */
    fun omitTrailingSlash(url: HttpUrl): HttpUrl {
        val idxLast = url.pathSize - 1
        val hasTrailingSlash = url.pathSegments[idxLast] == ""

        return if (hasTrailingSlash)
            url.newBuilder().removePathSegment(idxLast).build()
        else
            url
    }

    /**
     * Ensures that a given URL has a trailing slash after member names.
     *
     * @param url URL to process (e.g. 'http://host/test1')
     *
     * @return URL with trailing slash, e.g. `http://host/test1/`
     */
    fun withTrailingSlash(url: HttpUrl): HttpUrl {
        val idxLast = url.pathSize - 1
        val hasTrailingSlash = url.pathSegments[idxLast] == ""

        return if (hasTrailingSlash)
            url
        else
            url.newBuilder().addPathSegment("").build()
    }

}

/**
 * Compares two [HttpUrl]s in WebDAV context. If two URLs are considered *equal*, both
 * represent the same WebDAV resource.
 *
 * The fragment of an URL is ignored, e.g. `http://host:80/folder1` and `http://HOST/folder1#somefragment`
 * are considered to be equal.
 *
 * [HttpUrl] is less strict than [java.net.URI] and allows for instance (not encoded) square brackets in the path.
 * So this method tries to normalize the URI by converting it to a [java.net.URI] (encodes for instance square brackets)
 * and then comparing the scheme and scheme-specific part (without fragment).
 *
 * Attention: **This method does not deal with trailing slashes**, so if you want to compare collection URLs,
 * make sure they both (don't) have a trailing slash before calling this method, for instance
 * with [omitTrailingSlash] or [withTrailingSlash].
 *
 * @param other the URL to compare the current object with
 *
 * @return whether the URLs are considered to represent the same WebDAV resource
 */
fun HttpUrl.equalsForWebDAV(other: HttpUrl): Boolean {
    // if okhttp thinks the two URLs are equal, they're in any case
    // (and it's a simple String comparison)
    if (this == other)
        return true

    // convert to java.net.URI (also corrects some mistakes and escapes for instance square brackets)
    val uri1 = toUri()
    val uri2 = other.toUri()

    // if the URIs are the same (ignoring scheme case and fragments), they're equal for us
    return uri1.scheme.equals(uri2.scheme, true) && uri1.schemeSpecificPart == uri2.schemeSpecificPart
}