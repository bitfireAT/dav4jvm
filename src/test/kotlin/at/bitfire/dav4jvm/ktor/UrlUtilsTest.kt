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
        assertEquals(Url("http://host/resource"), Url("http://host/resource").omitTrailingSlash())
        assertEquals(Url("http://host/resource"), Url("http://host/resource/").omitTrailingSlash())
        assertEquals(Url("http://host"), Url("http://host").omitTrailingSlash())
        assertEquals(Url("http://host"), Url("http://host/").omitTrailingSlash())
        assertEquals(Url("http://host/resource?q=1"), Url("http://host/resource?q=1").omitTrailingSlash())
    }

    @Test
    fun testWithTrailingSlash() {
        assertEquals(Url("http://host/resource/"), Url("http://host/resource").withTrailingSlash())
        assertEquals(Url("http://host/resource/"), Url("http://host/resource/").withTrailingSlash())
        assertEquals(Url("http://host/"), Url("http://host").withTrailingSlash())
        assertEquals(Url("http://host/"), Url("http://host/").withTrailingSlash())
        assertEquals(Url("http://host/resource/?q=1"), Url("http://host/resource?q=1").withTrailingSlash())
    }

    @Test
    fun testUrl_Parent() {
        // Root URL - should return itself
        assertEquals(Url("http://host/"), Url("http://host/").parent())
        assertEquals(Url("http://host/"), Url("http://host").parent())

        // Single level with trailing slash
        assertEquals(Url("http://host/"), Url("http://host/folder/").parent())

        // Single level without trailing slash
        assertEquals(Url("http://host/"), Url("http://host/folder").parent())

        // Multiple levels with trailing slash
        assertEquals(Url("http://host/folder/"), Url("http://host/folder/subfolder/").parent())

        // Multiple levels without trailing slash
        assertEquals(Url("http://host/folder/"), Url("http://host/folder/subfolder").parent())

        // Deep nested
        assertEquals(Url("http://host/a/b/"), Url("http://host/a/b/c/").parent())
        assertEquals(Url("http://host/a/b/"), Url("http://host/a/b/c").parent())

        // With query parameters
        assertEquals(Url("http://host/?q=1"), Url("http://host/folder/?q=1").parent())
        assertEquals(Url("http://host/folder/?q=1"), Url("http://host/folder/subfolder/?q=1").parent())
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
        assertFalse(Url("http://host/resource").equalsForWebDAV(Url("https://host/resource")))

        assertTrue(Url("https://www.example.com/folder/[X]Y!.txt").equalsForWebDAV(Url("https://www.example.com/folder/[X]Y!.txt")))
        assertTrue(Url("https://www.example.com/folder/%5BX%5DY!.txt").equalsForWebDAV(Url("https://www.example.com/folder/[X]Y!.txt")))
        assertTrue(Url("https://www.example.com/folder/%5bX%5dY%21.txt").equalsForWebDAV(Url("https://www.example.com/folder/[X]Y!.txt")))
    }

    @Test
    fun testUrl_Resolve_Collection() {
        val collection = Url("https://example.com/base/path/")

        // relative path
        assertEquals(Url("https://example.com/base/path/relative"), collection.resolve("relative"))
        assertEquals(Url("https://example.com/base/path/subdir/file"), collection.resolve("subdir/file"))

        // absolute path
        assertEquals(Url("https://example.com/absolute"), collection.resolve("/absolute"))
        assertEquals(Url("https://example.com/"), collection.resolve("/"))

        // absolute URL
        assertEquals(Url("https://other.com/path"), collection.resolve("https://other.com/path"))
        assertEquals(Url("http://example.org/test"), collection.resolve("http://example.org/test"))
    }

    @Test
    fun testUrl_Resolve_NonCollection() {
        val baseUrl = Url("https://example.com/base")

        // relative path
        assertEquals(Url("https://example.com/relative"), baseUrl.resolve("relative"))
        assertEquals(Url("https://example.com/subdir/file"), baseUrl.resolve("subdir/file"))

        // absolute path
        assertEquals(Url("https://example.com/absolute"), baseUrl.resolve("/absolute"))
        assertEquals(Url("https://example.com/"), baseUrl.resolve("/"))

        // absolute URL
        assertEquals(Url("https://other.com/path"), baseUrl.resolve("https://other.com/path"))
        assertEquals(Url("http://example.org/test"), baseUrl.resolve("http://example.org/test"))
    }

    @Test
    fun `toUrlOrNull with invalid mailto URL`() {
        assertNull("mailto:invalid".toUrlOrNull())
    }

    @Test
    fun `toUrlOrNull with invalid HTTPS URL that can't be decoded`() {
        assertNull("https://example.com/%f".toUrlOrNull())
    }

    @Test
    fun `toUrlOrNull with valid HTTPS URL`() {
        assertEquals(
            Url("https://example.com"),
            "https://example.com".toUrlOrNull()
        )
    }

    @Test
    fun `toUrlOrNull with valid relative URL`() {
        assertEquals(
            Url("relative"),
            "relative".toUrlOrNull()
        )
    }

    @Test
    fun `toUrlOrNull with null`() =
        assertNull(null.toUrlOrNull())

}