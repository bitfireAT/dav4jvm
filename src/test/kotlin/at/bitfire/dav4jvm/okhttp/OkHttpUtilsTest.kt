/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

package at.bitfire.dav4jvm.okhttp

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Test

class OkHttpUtilsTest {

    @Test
    fun fileName() {
        assertEquals("", OkHttpUtils.fileName("https://example.com".toHttpUrl()))
        assertEquals("", OkHttpUtils.fileName("https://example.com/".toHttpUrl()))
        assertEquals("file1", OkHttpUtils.fileName("https://example.com/file1".toHttpUrl()))
        assertEquals("dir1", OkHttpUtils.fileName("https://example.com/dir1/".toHttpUrl()))
        assertEquals("file2", OkHttpUtils.fileName("https://example.com/dir1/file2".toHttpUrl()))
        assertEquals("dir2", OkHttpUtils.fileName("https://example.com/dir1/dir2/".toHttpUrl()))
    }
}