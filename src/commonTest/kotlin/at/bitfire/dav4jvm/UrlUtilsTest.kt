/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.ktor.http.*
import kotlin.test.assertEquals
import kotlin.test.assertNull

object UrlUtilsTest : FunSpec({

    fun String.toHttpUrlOrNull() = Url(this)

    withData(
        "http://host/resource".toHttpUrlOrNull() to "http://host/resource".toHttpUrlOrNull(),
        "http://host:80/resource".toHttpUrlOrNull() to "http://host/resource".toHttpUrlOrNull(),
        "https://HOST:443/resource".toHttpUrlOrNull() to "https://host/resource".toHttpUrlOrNull(),
        "https://host:443/my@dav/".toHttpUrlOrNull() to "https://host/my%40dav/".toHttpUrlOrNull(),
        "http://host/resource".toHttpUrlOrNull() to "http://host/resource#frag1".toHttpUrlOrNull(),
        "https://host/%5bresource%5d/".toHttpUrlOrNull() to "https://host/[resource]/".toHttpUrlOrNull()
    ) { (a, b) ->
        withClue("$a shouldEqual $b") {
            UrlUtils.equals(a, b).shouldBeTrue()
        }
    }

    withData(
        "http://host/resource".toHttpUrlOrNull() to "http://host/resource/".toHttpUrlOrNull(),
        "http://host/resource".toHttpUrlOrNull() to "http://host:81/resource".toHttpUrlOrNull()
    ) { (a, b) ->
        withClue("$a shouldNotEqual $b") {
            UrlUtils.equals(a, b).shouldBeFalse()
        }
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
