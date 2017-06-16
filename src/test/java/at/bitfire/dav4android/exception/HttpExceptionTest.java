/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.dav4android.exception;

import org.junit.Test;

import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

@SuppressWarnings("ThrowableInstanceNeverThrown")
public class HttpExceptionTest {

    final static String responseMessage = "Unknown error";

    @Test
    public void testHttpFormatting() {
        Request request = new Request.Builder()
                .post(RequestBody.create(null, "REQUEST\nBODY" + (char)5))
                .url("http://example.com")
                .build();

        Response response = new Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_2)
                .code(500)
                .message(responseMessage)
                .body(ResponseBody.create(null, (char)0x99 + "SERVER\r\nRESPONSE"))
                .build();
        HttpException e = new HttpException(response);
        assertTrue(e.getMessage().contains("500"));
        assertTrue(e.getMessage().contains(responseMessage));
        assertTrue(e.getRequest().contains("REQUEST[LF]\nBODY[05]"));
        assertTrue(e.getResponse().contains("[99]SERVER[CR][LF]\nRESPONSE"));
    }

}
