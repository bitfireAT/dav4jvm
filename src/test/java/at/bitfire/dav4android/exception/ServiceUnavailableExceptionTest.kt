/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.dav4android.exception

import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.internal.http.HttpDate
import org.junit.Assert.*
import org.junit.Test
import java.util.*

class ServiceUnavailableExceptionTest {

    @Test
    fun testRetryAfter() {
        var response = Response.Builder()
                .request(Request.Builder()
                        .url("http://www.example.com")
                        .get()
                        .build())
                .protocol(Protocol.HTTP_1_1)
                .code(503).message("Try later")
                .build()

        var e = ServiceUnavailableException(response)
        assertNull(e.retryAfter)

        response = response.newBuilder()
                .header("Retry-After", "120")
                .build()
        e = ServiceUnavailableException(response)
        assertNotNull(e.retryAfter)
        assertTrue(withinTimeRange(e.retryAfter!!, 120))

        val cal = Calendar.getInstance()
        cal.add(Calendar.MINUTE, 30)
        response = response.newBuilder()
                .header("Retry-After", HttpDate.format(cal.time))
                .build()
        e = ServiceUnavailableException(response)
        assertNotNull(e.retryAfter)
        assertTrue(withinTimeRange(e.retryAfter!!, 30*60))
    }


    private fun withinTimeRange(d: Date, seconds: Int): Boolean {
        val msCheck = d.time
        val msShouldBe = Date().time + seconds*1000
        // assume max. 5 seconds difference for test running
        return Math.abs(msCheck - msShouldBe) < 5000
    }

}