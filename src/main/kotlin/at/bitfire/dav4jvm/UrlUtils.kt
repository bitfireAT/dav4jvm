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

    fun equals(url1: HttpUrl, url2: HttpUrl): Boolean {
        // if okhttp thinks the two URLs are equal, they're in any case
        // (and it's a simple String comparison)
        if (url1 == url2)
            return true

        val uri1 = url1.uri()
        val uri2 = url2.uri()
        return try {
            val decoded1 = URI(uri1.scheme, uri1.schemeSpecificPart, uri1.fragment)
            val decoded2 = URI (uri2.scheme, uri2.schemeSpecificPart, uri2.fragment)
            decoded1 == decoded2
        } catch (e: URISyntaxException) {
            false
        }
    }

    fun hostToDomain(host: String?): String? {
        if (host == null)
            return null

        // remove optional dot at end
        @Suppress("NAME_SHADOWING")
        val host = host.removeSuffix(".")

        // split into labels
        val labels = host.split('.')
        return if (labels.size >= 2) {
            labels[labels.size - 2] + "." + labels[labels.size - 1]
        } else
            host
    }

    fun omitTrailingSlash(url: HttpUrl): HttpUrl {
        val idxLast = url.pathSize () - 1
        val hasTrailingSlash = url.pathSegments()[idxLast] == ""

        return if (hasTrailingSlash)
            url.newBuilder().removePathSegment(idxLast).build()
        else
            url
    }

    fun withTrailingSlash(url: HttpUrl): HttpUrl {
        val idxLast = url.pathSize() - 1
        val hasTrailingSlash = url.pathSegments()[idxLast] == ""

        return if (hasTrailingSlash)
            url
        else
            url.newBuilder().addPathSegment("").build()
    }

}
