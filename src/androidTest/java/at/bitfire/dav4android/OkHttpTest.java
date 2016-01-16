/*
 * Copyright © 2013 – 2016 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.dav4android;

import junit.framework.TestCase;

import java.io.IOException;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

public class OkHttpTest extends TestCase {

    private OkHttpClient httpClient = new OkHttpClient();
    private MockWebServer mockServer = new MockWebServer();

    @Override
    public void setUp() throws IOException {
        mockServer.start();
    }

    @Override
    public void tearDown() throws IOException {
        mockServer.shutdown();
    }


    public void testMultipleHeaders() throws IOException {
        mockServer.enqueue(new MockResponse()
                        .setResponseCode(200)
                        .addHeader("Test", "A, B")
                        .addHeader("Test", "C, D")
        );
        Response response = httpClient.newCall(new Request.Builder()
                .url(mockServer.url("/"))
                .build()).execute();
        assertEquals(200, response.code());
        //assertEquals("A, B, C, D", response.header("Test"));
    }

}
