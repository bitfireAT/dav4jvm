/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.dav4android.exception;

import com.squareup.okhttp.Headers;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import java.io.IOException;
import java.io.Serializable;

import okio.Buffer;

public class HttpException extends Exception implements Serializable {

    public final int status;
    public final String message;

    public final String request, response;

    public HttpException(String message) {
        super(message);
        this.message = message;

        this.status = -1;
        this.request = this.response = null;
    }

    public HttpException(int status, String message) {
        super(status + " " + message);
        this.status = status;
        this.message = message;

        request = response = null;
    }

    public HttpException(Response response) {
        super(response.code() + " " + response.message());

        status = response.code();
        message = response.message();

        /* As we don't know the media type and character set of request and response body,
           only printable ASCII characters will be shown in clear text. Other octets will
           be shown as "[xx]" where xx is the hex value of the octet.
         */

        // format request
        Request request = response.request();
        StringBuilder formatted = new StringBuilder();
        formatted.append(request.method() + " " + request.urlString() + "\n");
        Headers headers = request.headers();
        for (String name : headers.names())
            for (String value : headers.values(name))
                formatted.append(name + ": " + value + "\n");
        if (request.body() != null)
            try {
                formatted.append("\n");
                Buffer buffer = new Buffer();
                request.body().writeTo(buffer);
                while (!buffer.exhausted())
                    appendByte(formatted, buffer.readByte());
            } catch (IOException e) {
            }
        this.request = formatted.toString();

        // format response
        formatted = new StringBuilder();
        formatted.append(response.protocol() + " " + response.code() + " " + response.message() + "\n");
        headers = response.headers();
        for (String name : headers.names())
            for (String value : headers.values(name))
                formatted.append(name + ": " + value + "\n");
        if (response.body() != null)
            try {
                formatted.append("\n");
                for (byte b : response.body().bytes())
                    appendByte(formatted, b);
            } catch (IOException e) {
            }
        this.response = formatted.toString();
    }

    private static void appendByte(StringBuilder formatted, byte b) {
        if (b == '\r')
            formatted.append("[CR]");
        else if (b == '\n')
            formatted.append("[LF]\n");
        else if (b >= 0x20 && b <= 0x7E)     // printable ASCII
            formatted.append((char)b);
        else
            formatted.append("[" + Integer.toHexString(b) + "]");
    }

}
