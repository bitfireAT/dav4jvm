/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm

import io.kotest.core.spec.style.FunSpec
import io.ktor.http.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

object UrlUtilsTest : FunSpec({

    fun String.toHttpUrlOrNull() = Url(this)

    test("testEquals") {
        assertTrue(
            UrlUtils.equals(
                "http://host/resource".toHttpUrlOrNull(),
                "http://host/resource".toHttpUrlOrNull()
            )
        )
        assertTrue(
            UrlUtils.equals(
                "http://host:80/resource".toHttpUrlOrNull(),
                "http://host/resource".toHttpUrlOrNull()
            )
        )
        assertTrue(
            UrlUtils.equals(
                "https://HOST:443/resource".toHttpUrlOrNull(),
                "https://host/resource".toHttpUrlOrNull()
            )
        )
        assertTrue(
            UrlUtils.equals(
                "https://host:443/my@dav/".toHttpUrlOrNull(),
                "https://host/my%40dav/".toHttpUrlOrNull()
            )
        )
        assertTrue(
            UrlUtils.equals(
                "http://host/resource".toHttpUrlOrNull(),
                "http://host/resource#frag1".toHttpUrlOrNull()
            )
        )

        // should work, but currently doesn't (see MR #5)
        // assertTrue(UrlUtils.equals(HttpUrl.parse("https://host/%5bresource%5d/")!!, HttpUrl.parse("https://host/[resource]/")!!))

        assertFalse(
            UrlUtils.equals(
                "http://host/resource".toHttpUrlOrNull(),
                "http://host/resource/".toHttpUrlOrNull()
            )
        )
        assertFalse(
            UrlUtils.equals(
                "http://host/resource".toHttpUrlOrNull(),
                "http://host:81/resource".toHttpUrlOrNull()
            )
        )
    }

    test("testHostToDomain") {
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

    test("testOmitTrailingSlash") {
        assertEquals(
            "http://host/resource".toHttpUrlOrNull(),
            UrlUtils.omitTrailingSlash("http://host/resource".toHttpUrlOrNull())
        )
        assertEquals(
            "http://host/resource".toHttpUrlOrNull(),
            UrlUtils.omitTrailingSlash("http://host/resource/".toHttpUrlOrNull())
        )
    }

    test("testWithTrailingSlash") {
        assertEquals(
            "http://host/resource/".toHttpUrlOrNull(),
            UrlUtils.withTrailingSlash("http://host/resource".toHttpUrlOrNull())
        )
        assertEquals(
            "http://host/resource/".toHttpUrlOrNull(),
            UrlUtils.withTrailingSlash("http://host/resource/".toHttpUrlOrNull())
        )
    }

})
