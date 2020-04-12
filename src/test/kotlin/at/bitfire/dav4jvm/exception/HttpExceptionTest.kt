/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm.exception

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpExceptionTest {

    private val responseMessage = "Unknown error"

    @Test
    fun testHttpFormatting() {
        val request = Request.Builder()
                .post("REQUEST\nBODY".toRequestBody("text/something".toMediaType()))
                .url("http://example.com")
                .build()

        val response = Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(500)
                .message(responseMessage)
                .body("SERVER\r\nRESPONSE".toResponseBody("text/something-other".toMediaType()))
                .build()
        val e = HttpException(response)
        assertTrue(e.message!!.contains("500"))
        assertTrue(e.message!!.contains(responseMessage))
        assertTrue(e.requestBody!!.contains("REQUEST\nBODY"))
        assertTrue(e.responseBody!!.contains("SERVER\r\nRESPONSE"))
    }

}
