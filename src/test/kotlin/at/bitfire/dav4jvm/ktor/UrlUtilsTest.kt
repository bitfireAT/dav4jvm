/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

package at.bitfire.dav4jvm.ktor

import io.ktor.http.Url
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
        assertEquals(Url("http://host/resource"), UrlUtils.omitTrailingSlash(Url("http://host/resource")))
        assertEquals(Url("http://host/resource"), UrlUtils.omitTrailingSlash(Url("http://host/resource/")))
    }

    @Test
    fun testWithTrailingSlash() {
        assertEquals(Url("http://host/resource/"), UrlUtils.withTrailingSlash(Url("http://host/resource")))
        assertEquals(Url("http://host/resource/"), UrlUtils.withTrailingSlash(Url("http://host/resource/")))
    }


    @Test
    fun testHttpUrl_EqualsForWebDAV() {
        assertTrue(Url("http://host/resource").equalsForWebDAV(Url("http://host/resource")))
        assertTrue(Url("http://host:80/resource").equalsForWebDAV(Url("http://host/resource")))
        assertTrue(Url("https://HOST:443/resource").equalsForWebDAV(Url("https://host/resource")))
        assertTrue(Url("https://host:443/my@dav/").equalsForWebDAV(Url("https://host/my%40dav/")))
        assertTrue(Url("http://host/resource").equalsForWebDAV(Url("http://host/resource#frag1")))

        assertFalse(Url("http://host/resource").equalsForWebDAV(Url("http://host/resource/")))
        assertFalse(Url("http://host/resource").equalsForWebDAV(Url("http://host:81/resource")))

        assertTrue(Url("https://www.example.com/folder/[X]Y!.txt").equalsForWebDAV(Url("https://www.example.com/folder/[X]Y!.txt")))
        assertTrue(Url("https://www.example.com/folder/%5BX%5DY!.txt").equalsForWebDAV(Url("https://www.example.com/folder/[X]Y!.txt")))
        assertTrue(Url("https://www.example.com/folder/%5bX%5dY%21.txt").equalsForWebDAV(Url("https://www.example.com/folder/[X]Y!.txt")))
    }

}