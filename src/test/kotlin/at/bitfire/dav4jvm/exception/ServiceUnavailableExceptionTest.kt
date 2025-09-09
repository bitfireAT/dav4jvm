/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

package at.bitfire.dav4jvm.exception

import at.bitfire.dav4jvm.HttpUtils
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class ServiceUnavailableExceptionTest {

    val response503 = Response.Builder()
        .request(
            Request.Builder()
                .url("http://www.example.com")
                .get()
                .build()
        )
        .protocol(Protocol.HTTP_1_1)
        .code(503).message("Try later")
        .build()

    @Test
    fun testRetryAfter_NoTime() {
        val e = ServiceUnavailableException(response503)
        assertNull(e.retryAfter)
    }

    @Test
    fun testRetryAfter_Seconds() {
        val response = response503.newBuilder()
            .header("Retry-After", "120")
            .build()
        val e = ServiceUnavailableException(response)
        assertNotNull(e.retryAfter)
        assertTrue(withinTimeRange(e.retryAfter!!, 120))
    }

    @Test
    fun testRetryAfter_Date() {
        val after30min = Instant.now().plusSeconds(30 * 60)
        val response = response503.newBuilder()
            .header("Retry-After", HttpUtils.formatDate(after30min))
            .build()
        val e = ServiceUnavailableException(response)
        assertNotNull(e.retryAfter)
        assertTrue(withinTimeRange(e.retryAfter!!, 30 * 60))
    }


    private fun withinTimeRange(d: Instant, seconds: Long) =
        d.isBefore(
            Instant.now()
                .plusSeconds(seconds)
                .plusSeconds(5)     // tolerance for test running
        )

}