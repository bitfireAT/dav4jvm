/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.junit.Assert.assertEquals
import org.junit.Test

class HttpUtilsTest {

    @Test
    fun testFilename() {
        val sampleUrl = "https://example.com".toHttpUrlOrNull()!!
        assertEquals("", HttpUtils.fileName(sampleUrl.resolve("/")!!))
        assertEquals("hier1", HttpUtils.fileName(sampleUrl.resolve("/hier1")!!))
        assertEquals("", HttpUtils.fileName(sampleUrl.resolve("/hier1/")!!))
        assertEquals("hier2", HttpUtils.fileName(sampleUrl.resolve("/hier2")!!))
        assertEquals("", HttpUtils.fileName(sampleUrl.resolve("/hier2/")!!))
    }

}
