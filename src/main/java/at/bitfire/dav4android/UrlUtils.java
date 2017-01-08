package at.bitfire.dav4android;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;

import okhttp3.HttpUrl;

public class UrlUtils {

    public static boolean equals(@NonNull HttpUrl url1, @NonNull HttpUrl url2) {
        // if okhttp thinks the two URLs are equal, they're in any case
        // (and it's a simple String comparison)
        if (url1.equals(url2))
            return true;

        URI uri1 = url1.uri(), uri2 = url2.uri();
        try {
            URI decoded1 = new URI(uri1.getScheme(), uri1.getSchemeSpecificPart(), uri1.getFragment()),
                decoded2 = new URI(uri2.getScheme(), uri2.getSchemeSpecificPart(), uri2.getFragment());
            return decoded1.equals(decoded2);
        } catch (URISyntaxException e) {
            return false;
        }
    }

    public static String hostToDomain(@Nullable String host) {
        if (host == null)
            return null;

        // remove optional dot at end
        host = StringUtils.removeEnd(host, ".");

        // split into labels
        String labels[] = StringUtils.split(host, '.');
        if (labels.length >= 2) {
            return labels[labels.length - 2] + "." + labels[labels.length - 1];
        } else
            return host;
    }

    public static HttpUrl omitTrailingSlash(@NonNull HttpUrl url) {
        int idxLast = url.pathSize() - 1;
        boolean hasTrailingSlash = "".equals(url.pathSegments().get(idxLast));

        if (hasTrailingSlash)
            return url.newBuilder().removePathSegment(idxLast).build();
        else
            return url;
    }

    public static HttpUrl withTrailingSlash(@NonNull HttpUrl url) {
        int idxLast = url.pathSize() - 1;
        boolean hasTrailingSlash = "".equals(url.pathSegments().get(idxLast));

        if (hasTrailingSlash)
            return url;
        else
            return url.newBuilder().addPathSegment("").build();
    }

}
