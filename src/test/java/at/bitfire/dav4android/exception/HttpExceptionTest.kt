/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.dav4android.exception

import okhttp3.*
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpExceptionTest {

    private val responseMessage = "Unknown error"

    @Test
    fun testHttpFormatting() {
        val request = Request.Builder()
                .post(RequestBody.create(null, "REQUEST\nBODY" + 5.toChar()))
                .url("http://example.com")
                .build()

        val response = Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_2)
                .code(500)
                .message(responseMessage)
                .body(ResponseBody.create(null, 0x99.toChar() + "SERVER\r\nRESPONSE"))
                .build()
        val e = HttpException(response)
        assertTrue(e.message!!.contains("500"))
        assertTrue(e.message!!.contains(responseMessage))
        assertTrue(e.request!!.contains("REQUEST\nBODY[05]"))
        assertTrue(e.response!!.contains("[99]SERVER↵\nRESPONSE"))
    }

}
