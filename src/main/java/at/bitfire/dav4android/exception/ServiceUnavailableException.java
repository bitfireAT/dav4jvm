/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.dav4android.exception;

import com.squareup.okhttp.Response;
import com.squareup.okhttp.internal.http.HttpDate;

import java.net.HttpURLConnection;
import java.util.Calendar;
import java.util.Date;

import at.bitfire.dav4android.Constants;

public class ServiceUnavailableException extends HttpException {

    public Date retryAfter;

    public ServiceUnavailableException(String message) {
        super(HttpURLConnection.HTTP_UNAVAILABLE, message);
        retryAfter = null;
    }

    public ServiceUnavailableException(Response response) {
        super(response);

        // Retry-After  = "Retry-After" ":" ( HTTP-date | delta-seconds )
        // HTTP-date    = rfc1123-date | rfc850-date | asctime-date

        String strRetryAfter = response.header("Retry-After");
        if (strRetryAfter != null) {
            retryAfter = HttpDate.parse(strRetryAfter);

            if (retryAfter == null)
                // not a HTTP-date, must be delta-seconds
                try {
                    int seconds = Integer.parseInt(strRetryAfter);

                    Calendar cal = Calendar.getInstance();
                    cal.add(Calendar.SECOND, seconds);
                    retryAfter = cal.getTime();

                } catch (NumberFormatException e) {
                    Constants.log.warn("Received Retry-After which was not a HTTP-date nor delta-seconds");
                }
        }
    }

}
