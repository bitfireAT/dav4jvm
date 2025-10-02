/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

package at.bitfire.dav4jvm.okhttp.exception

import at.bitfire.dav4jvm.Error
import at.bitfire.dav4jvm.Property
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HttpResponseInfoTest {

    // requestExcerpt

    @Test
    fun `requestExcerpt (binary blob)`() {
        val request = Request.Builder()
            .post("Sample".toRequestBody("application/test".toMediaType()))
            .url("https://example.com")
            .build()
        val result = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(204)
            .message("No Content")
            .build()
            .use { response ->
                HttpResponseInfo.fromResponse(response)
            }
        assertEquals("POST https://example.com/\n\n<request body (6 bytes)>", result.requestExcerpt)
    }

    @Test
    fun `requestExcerpt (large CSS text)`() {
        val request = Request.Builder()
            .post("*".repeat(DavException.MAX_EXCERPT_SIZE * 2).toRequestBody("text/css".toMediaType()))
            .url("https://example.com")
            .build()
        val result = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(204)
            .message("No Content")
            .build()
            .use { response ->
                HttpResponseInfo.fromResponse(response)
            }
        val truncatedText = "*".repeat(DavException.MAX_EXCERPT_SIZE)
        assertEquals("POST https://example.com/\n\n$truncatedText", result.requestExcerpt)
    }


    // responseExcerpt

    @Test
    fun `responseExcerpt (binary blob)`() {
        val request = Request.Builder()
            .get()
            .url("https://example.com")
            .build()
        val result = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(404)
            .message("Not Found")
            .body("Evil binary data".toResponseBody("application/octet-stream".toMediaType()))
            .build()
            .use { response ->
                HttpResponseInfo.fromResponse(response)
            }
        assertNull(result.responseExcerpt)
    }

    @Test
    fun `responseExcerpt (HTML)`() {
        val request = Request.Builder()
            .get()
            .url("https://example.com")
            .build()
        val result = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(404)
            .message("Not Found")
            .body("Interesting details about error".toResponseBody("text/html".toMediaType()))
            .build()
            .use { response ->
                HttpResponseInfo.fromResponse(response)
            }
        assertEquals("Interesting details about error", result.responseExcerpt)
    }

    @Test
    fun `responseExcerpt (large HTML)`() {
        val request = Request.Builder()
            .get()
            .url("https://example.com")
            .build()
        val result = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(404)
            .message("Not Found")
            .body("0123456789".repeat(3 * 1024).toResponseBody("text/html".toMediaType()))    // 30 kB
            .build()
            .use { response ->
                HttpResponseInfo.fromResponse(response)
            }
        assertEquals(
            "0123456789".repeat(2 * 1024),    // limited to 20 kB
            result.responseExcerpt
        )
    }

    @Test
    fun `responseExcerpt (closed response)`() {
        val request = Request.Builder()
            .get()
            .url("https://example.com")
            .build()
        val response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body("Some Response".toResponseBody())
            .build()

        response.close()

        val result = HttpResponseInfo.fromResponse(response)
        assertNull(result.responseExcerpt)
    }

    @Test
    fun `responseExcerpt (no MIME type)`() {
        val request = Request.Builder()
            .get()
            .url("https://example.com")
            .build()
        val result = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body("Maybe evil binary data".toResponseBody())
            .build()
            .use { response ->
                HttpResponseInfo.fromResponse(response)
            }

        assertNull(result.responseExcerpt)
    }

    @Test
    fun `responseExcerpt (XML with error elements)`() {
        val request = Request.Builder()
            .get()
            .url("https://example.com")
            .build()
        val xml = """
            <?xml version="1.0" encoding="utf-8" ?>
            <D:error xmlns:D="DAV:">
                <D:lock-token-submitted>
                    <D:href>/locked/</D:href>
                </D:lock-token-submitted>
            </D:error>
            """.trimIndent()
        val result = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(xml.toResponseBody("application/xml".toMediaType()))
            .build().use { response ->
                HttpResponseInfo.fromResponse(response)
            }

        assertEquals(xml, result.responseExcerpt)
        assertEquals(
            listOf(
                Error(Property.Name("DAV:", "lock-token-submitted"))
            ),
            result.errors
        )
    }

}