package at.bitfire.dav4android;

import com.squareup.okhttp.HttpUrl;

import junit.framework.TestCase;

public class UrlUtilsTest extends TestCase {

    public void testEquals() {
        assertTrue(UrlUtils.equals(HttpUrl.parse("http://host/resource"), HttpUrl.parse("http://host/resource")));
        assertTrue(UrlUtils.equals(HttpUrl.parse("http://host:80/resource"), HttpUrl.parse("http://host/resource")));
        assertTrue(UrlUtils.equals(HttpUrl.parse("https://HOST:443/resource"), HttpUrl.parse("https://host/resource")));
        assertTrue(UrlUtils.equals(HttpUrl.parse("https://host:443/my@dav/"), HttpUrl.parse("https://host/my%40dav/")));

        assertFalse(UrlUtils.equals(HttpUrl.parse("http://host/resource"), HttpUrl.parse("http://host/resource/")));
        assertFalse(UrlUtils.equals(HttpUrl.parse("http://host/resource"), HttpUrl.parse("http://host:81/resource")));
    }

    public void testOmitTrailingSlash() {
        assertEquals(HttpUrl.parse("http://host/resource"), UrlUtils.omitTrailingSlash(HttpUrl.parse("http://host/resource")));
        assertEquals(HttpUrl.parse("http://host/resource"), UrlUtils.omitTrailingSlash(HttpUrl.parse("http://host/resource/")));
    }

    public void testWithTrailingSlash() {
        assertEquals(HttpUrl.parse("http://host/resource/"), UrlUtils.withTrailingSlash(HttpUrl.parse("http://host/resource")));
        assertEquals(HttpUrl.parse("http://host/resource/"), UrlUtils.withTrailingSlash(HttpUrl.parse("http://host/resource/")));
    }

}
