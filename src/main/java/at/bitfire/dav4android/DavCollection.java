package at.bitfire.dav4android;

import java.util.logging.Logger;

import lombok.NonNull;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;

public class DavCollection extends DavResource {

    public DavCollection(@NonNull OkHttpClient httpClient, @NonNull HttpUrl location) {
        super(httpClient, location);
    }

}
