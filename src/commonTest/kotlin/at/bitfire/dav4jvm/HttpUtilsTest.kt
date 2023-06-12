/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm

import io.kotest.core.spec.style.FunSpec
import io.ktor.http.*
import kotlin.test.assertEquals


object HttpUtilsTest: FunSpec({

    fun String.toHttpUrl() = Url(this)

    test("fileName") {
        assertEquals("", HttpUtils.fileName("https://example.com".toHttpUrl()))
        assertEquals("", HttpUtils.fileName("https://example.com/".toHttpUrl()))
        assertEquals("file1", HttpUtils.fileName("https://example.com/file1".toHttpUrl()))
        assertEquals("dir1", HttpUtils.fileName("https://example.com/dir1/".toHttpUrl()))
        assertEquals("file2", HttpUtils.fileName("https://example.com/dir1/file2".toHttpUrl()))
        assertEquals("dir2", HttpUtils.fileName("https://example.com/dir1/dir2/".toHttpUrl()))
    }

    test("parseDate") {
        assertEquals(1683825995000, HttpUtils.parseDate("Thu, 11 May 2023 17:26:35 GMT")?.local?.unixMillisLong)
    }

})
