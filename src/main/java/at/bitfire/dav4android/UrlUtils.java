package at.bitfire.dav4android;

import com.squareup.okhttp.HttpUrl;

public class UrlUtils {

    static public HttpUrl omitTrailingSlash(HttpUrl url) {
        int idxLast = url.pathSize() - 1;
        boolean hasTrailingSlash = "".equals(url.pathSegments().get(idxLast));

        if (hasTrailingSlash)
            return url.newBuilder().removePathSegment(idxLast).build();
        else
            return url;
    }

    static public HttpUrl withTrailingSlash(HttpUrl url) {
        int idxLast = url.pathSize() - 1;
        boolean hasTrailingSlash = "".equals(url.pathSegments().get(idxLast));

        if (hasTrailingSlash)
            return url;
        else
            return url.newBuilder().addPathSegment("").build();
    }

}
