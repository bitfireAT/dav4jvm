package at.bitfire.dav4android;

import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.util.logging.Logger

open class DavCollection(
        httpClient: OkHttpClient,
        location: HttpUrl,
        log: Logger = Constants.log
): DavResource(httpClient, location, log) {
}
