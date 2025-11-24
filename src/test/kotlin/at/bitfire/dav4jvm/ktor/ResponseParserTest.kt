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
import org.junit.Test

class ResponseParserTest {

    val baseUrl = Url("https://example.com/collection/")
    val parser = ResponseParser(baseUrl, callback = { _, _ ->
        // no-op
    })

    @Test
    fun `resolveHref with absolute URL`() {
        assertEquals(
            Url("https://example.com/collection/member"),
            parser.resolveHref("https://example.com/collection/member")
        )
    }

    @Test
    fun `resolveHref with absolute path`() {
        assertEquals(
            Url("https://example.com/collection/member"),
            parser.resolveHref("/collection/member")
        )
    }

    @Test
    fun `resolveHref with relative path`() {
        assertEquals(
            Url("https://example.com/collection/member"),
            parser.resolveHref("member")
        )
    }

    @Test
    fun `resolveHref with relative path with colon`() {
        assertEquals(
            Url("https://example.com/collection/mem:ber"),
            parser.resolveHref("mem:ber")
        )
    }

}