package at.bitfire.dav4android;

import com.squareup.okhttp.HttpUrl;

import junit.framework.TestCase;

public class UrlUtilsTest extends TestCase {

    public void testOmitTrailingSlash() {
        assertEquals(HttpUrl.parse("http://host/resource"), UrlUtils.omitTrailingSlash(HttpUrl.parse("http://host/resource")));
        assertEquals(HttpUrl.parse("http://host/resource"), UrlUtils.omitTrailingSlash(HttpUrl.parse("http://host/resource/")));
    }

}
