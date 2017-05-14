/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.dav4android.exception;

import org.junit.Test;

import java.util.Calendar;
import java.util.Date;

import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.internal.http.HttpDate;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ServiceUnavailableExceptionTest {

    @Test
    public void testRetryAfter() {
        Response response = new Response.Builder()
                .request(new Request.Builder()
                        .url("http://www.example.com")
                        .get()
                        .build())
                .protocol(Protocol.HTTP_1_1)
                .code(503).message("Try later")
                .build();

        ServiceUnavailableException e = new ServiceUnavailableException(response);
        assertNull(e.retryAfter);

        response = response.newBuilder()
                .header("Retry-After", "120")
                .build();
        e = new ServiceUnavailableException(response);
        assertNotNull(e.retryAfter);
        assertTrue(withinTimeRange(e.retryAfter, 120));

        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.MINUTE, 30);
        response = response.newBuilder()
                .header("Retry-After", HttpDate.format(cal.getTime()))
                .build();
        e = new ServiceUnavailableException(response);
        assertNotNull(e.retryAfter);
        assertTrue(withinTimeRange(e.retryAfter, 30*60));
    }


    private boolean withinTimeRange(Date d, int seconds) {
        final long msCheck = d.getTime(), msShouldBe = new Date().getTime() + seconds*1000;
        // assume max. 5 seconds difference for test running
        return Math.abs(msCheck - msShouldBe) < 5000;
    }

}
