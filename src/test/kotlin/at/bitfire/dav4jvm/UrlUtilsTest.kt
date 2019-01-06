/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm

import okhttp3.HttpUrl
import org.junit.Assert.*
import org.junit.Test

class UrlUtilsTest {

    @Test
    fun testEquals() {
        assertTrue(UrlUtils.equals(HttpUrl.parse("http://host/resource")!!, HttpUrl.parse("http://host/resource")!!))
        assertTrue(UrlUtils.equals(HttpUrl.parse("http://host:80/resource")!!, HttpUrl.parse("http://host/resource")!!))
        assertTrue(UrlUtils.equals(HttpUrl.parse("https://HOST:443/resource")!!, HttpUrl.parse("https://host/resource")!!))
        assertTrue(UrlUtils.equals(HttpUrl.parse("https://host:443/my@dav/")!!, HttpUrl.parse("https://host/my%40dav/")!!))

        assertFalse(UrlUtils.equals(HttpUrl.parse("http://host/resource")!!, HttpUrl.parse("http://host/resource/")!!))
        assertFalse(UrlUtils.equals(HttpUrl.parse("http://host/resource")!!, HttpUrl.parse("http://host:81/resource")!!))
    }

    @Test
    fun testHostToDomain() {
        assertNull(UrlUtils.hostToDomain(null))
        assertEquals("", UrlUtils.hostToDomain("."))
        assertEquals("com", UrlUtils.hostToDomain("com"))
        assertEquals("com", UrlUtils.hostToDomain("com."))
        assertEquals("example.com", UrlUtils.hostToDomain("example.com"))
        assertEquals("example.com", UrlUtils.hostToDomain("example.com."))
        assertEquals("example.com", UrlUtils.hostToDomain(".example.com"))
        assertEquals("example.com", UrlUtils.hostToDomain(".example.com."))
        assertEquals("example.com", UrlUtils.hostToDomain("host.example.com"))
        assertEquals("example.com", UrlUtils.hostToDomain("host.example.com."))
        assertEquals("example.com", UrlUtils.hostToDomain("sub.host.example.com"))
        assertEquals("example.com", UrlUtils.hostToDomain("sub.host.example.com."))
    }

    @Test
    fun testOmitTrailingSlash() {
        assertEquals(HttpUrl.parse("http://host/resource")!!, UrlUtils.omitTrailingSlash(HttpUrl.parse("http://host/resource")!!))
        assertEquals(HttpUrl.parse("http://host/resource")!!, UrlUtils.omitTrailingSlash(HttpUrl.parse("http://host/resource/")!!))
    }

    @Test
    fun testWithTrailingSlash() {
        assertEquals(HttpUrl.parse("http://host/resource/")!!, UrlUtils.withTrailingSlash(HttpUrl.parse("http://host/resource")!!))
        assertEquals(HttpUrl.parse("http://host/resource/")!!, UrlUtils.withTrailingSlash(HttpUrl.parse("http://host/resource/")!!))
    }

}
