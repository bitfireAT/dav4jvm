package at.bitfire.dav4android;

import okhttp3.HttpUrl
import org.apache.commons.lang3.StringUtils
import java.net.URI
import java.net.URISyntaxException

class UrlUtils {
    companion object {

        @JvmStatic
        fun equals(url1: HttpUrl, url2: HttpUrl): Boolean {
            // if okhttp thinks the two URLs are equal, they're in any case
            // (and it's a simple String comparison)
            if (url1 == url2)
                return true

            val uri1 = url1.uri()
            val uri2 = url2.uri()
            try {
                val decoded1 = URI(uri1.scheme, uri1.schemeSpecificPart, uri1.fragment)
                val decoded2 = URI (uri2.scheme, uri2.schemeSpecificPart, uri2.fragment)
                return decoded1 == decoded2
            } catch (e: URISyntaxException) {
                return false
            }
        }

        @JvmStatic
        fun hostToDomain(host: String?): String? {
            if (host == null)
                return null

            // remove optional dot at end
            @Suppress("NAME_SHADOWING")
            val host = StringUtils.removeEnd(host, ".")

            // split into labels
            val labels = StringUtils.split(host, '.')
            return if (labels.size >= 2) {
                labels[labels.size - 2] + "." + labels[labels.size - 1]
            } else
                host
        }

        @JvmStatic
        fun omitTrailingSlash(url: HttpUrl): HttpUrl {
            val idxLast = url.pathSize () - 1
            val hasTrailingSlash = url.pathSegments()[idxLast] == ""

            return if (hasTrailingSlash)
                url.newBuilder().removePathSegment(idxLast).build()
            else
                url
        }

        @JvmStatic
        fun withTrailingSlash(url: HttpUrl): HttpUrl {
            val idxLast = url.pathSize() - 1
            val hasTrailingSlash = url.pathSegments()[idxLast] == ""

            return if (hasTrailingSlash)
                url
            else
                url.newBuilder().addPathSegment("").build()
        }

    }
}
