/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.dav4android.exception;

import com.squareup.okhttp.Response;

import java.net.HttpURLConnection;

public class NotFoundException extends HttpException {

    public NotFoundException(Response response) {
        super(response);
    }

    public NotFoundException(String message) {
        super(HttpURLConnection.HTTP_NOT_FOUND, message);
    }

}
