package at.bitfire.dav4android;

import org.junit.Test;

import okhttp3.HttpUrl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UrlUtilsTest {

    @Test
    public void testEquals() {
        assertTrue(UrlUtils.equals(HttpUrl.parse("http://host/resource"), HttpUrl.parse("http://host/resource")));
        assertTrue(UrlUtils.equals(HttpUrl.parse("http://host:80/resource"), HttpUrl.parse("http://host/resource")));
        assertTrue(UrlUtils.equals(HttpUrl.parse("https://HOST:443/resource"), HttpUrl.parse("https://host/resource")));
        assertTrue(UrlUtils.equals(HttpUrl.parse("https://host:443/my@dav/"), HttpUrl.parse("https://host/my%40dav/")));

        assertFalse(UrlUtils.equals(HttpUrl.parse("http://host/resource"), HttpUrl.parse("http://host/resource/")));
        assertFalse(UrlUtils.equals(HttpUrl.parse("http://host/resource"), HttpUrl.parse("http://host:81/resource")));
    }

    @Test
    public void testOmitTrailingSlash() {
        assertEquals(HttpUrl.parse("http://host/resource"), UrlUtils.omitTrailingSlash(HttpUrl.parse("http://host/resource")));
        assertEquals(HttpUrl.parse("http://host/resource"), UrlUtils.omitTrailingSlash(HttpUrl.parse("http://host/resource/")));
    }

    @Test
    public void testWithTrailingSlash() {
        assertEquals(HttpUrl.parse("http://host/resource/"), UrlUtils.withTrailingSlash(HttpUrl.parse("http://host/resource")));
        assertEquals(HttpUrl.parse("http://host/resource/"), UrlUtils.withTrailingSlash(HttpUrl.parse("http://host/resource/")));
    }

}
