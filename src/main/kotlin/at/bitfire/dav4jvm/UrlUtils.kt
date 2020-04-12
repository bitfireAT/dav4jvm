/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm

import okhttp3.HttpUrl
import java.net.URI
import java.net.URISyntaxException

object UrlUtils {

    /**
     * Compares two URLs in WebDAV context. If two URLs are considered *equal*, both
     * represent the same WebDAV resource (e.g. `http://host:80/folder1` and `http://HOST/folder1#somefragment`).
     *
     * It decodes %xx entities in the path, so `/my@dav` and `/my%40dav` are considered the same.
     * This is important to process multi-status responses: some servers serve a multi-status
     * response with href `/my@dav` when you request `/my%40dav` and vice versa.
     *
     * This method does not deal with trailing slashes, so if you want to compare collection URLs,
     * make sure they both (don't) have a trailing slash before calling this method, for instance
     * with [omitTrailingSlash] or [withTrailingSlash].
     *
     * @param url1 the first URL to be compared
     * @param url2 the second URL to be compared
     *
     * @return whether [url1] and [url2] (usually) represent the same WebDAV resource
     */
    fun equals(url1: HttpUrl, url2: HttpUrl): Boolean {
        // if okhttp thinks the two URLs are equal, they're in any case
        // (and it's a simple String comparison)
        if (url1 == url2)
            return true

        // drop #fragment parts and convert to URI
        val uri1 = url1.newBuilder().fragment(null).build().toUri()
        val uri2 = url2.newBuilder().fragment(null).build().toUri()

        return try {
            val decoded1 = URI(uri1.scheme, uri1.schemeSpecificPart, uri1.fragment)
            val decoded2 = URI(uri2.scheme, uri2.schemeSpecificPart, uri2.fragment)
            decoded1 == decoded2
        } catch (e: URISyntaxException) {
            false
        }
    }

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
