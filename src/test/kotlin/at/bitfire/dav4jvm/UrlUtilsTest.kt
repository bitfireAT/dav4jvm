/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlUtilsTest {

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
        assertEquals("http://host/resource".toHttpUrl(), UrlUtils.omitTrailingSlash("http://host/resource".toHttpUrl()))
        assertEquals("http://host/resource".toHttpUrl(), UrlUtils.omitTrailingSlash("http://host/resource/".toHttpUrl()))
    }

    @Test
    fun testWithTrailingSlash() {
        assertEquals("http://host/resource/".toHttpUrl(), UrlUtils.withTrailingSlash("http://host/resource".toHttpUrl()))
        assertEquals("http://host/resource/".toHttpUrl(), UrlUtils.withTrailingSlash("http://host/resource/".toHttpUrl()))
    }


    @Test
    fun testHttpUrl_EqualsForWebDAV() {
        assertTrue("http://host/resource".toHttpUrl().equalsForWebDAV("http://host/resource".toHttpUrl()))
        assertTrue("http://host:80/resource".toHttpUrl().equalsForWebDAV("http://host/resource".toHttpUrl()))
        assertTrue("https://HOST:443/resource".toHttpUrl().equalsForWebDAV("https://host/resource".toHttpUrl()))
        assertTrue("https://host:443/my@dav/".toHttpUrl().equalsForWebDAV("https://host/my%40dav/".toHttpUrl()))
        assertTrue("http://host/resource".toHttpUrl().equalsForWebDAV("http://host/resource#frag1".toHttpUrl()))

        assertFalse("http://host/resource".toHttpUrl().equalsForWebDAV("http://host/resource/".toHttpUrl()))
        assertFalse("http://host/resource".toHttpUrl().equalsForWebDAV("http://host:81/resource".toHttpUrl()))

        assertTrue("https://www.example.com/folder/[X]Y!.txt".toHttpUrl().equalsForWebDAV("https://www.example.com/folder/[X]Y!.txt".toHttpUrl()))
        assertTrue("https://www.example.com/folder/%5BX%5DY!.txt".toHttpUrl().equalsForWebDAV("https://www.example.com/folder/[X]Y!.txt".toHttpUrl()))
        assertTrue("https://www.example.com/folder/%5bX%5dY%21.txt".toHttpUrl().equalsForWebDAV("https://www.example.com/folder/[X]Y!.txt".toHttpUrl()))
    }

}