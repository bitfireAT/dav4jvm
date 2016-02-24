/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.dav4android.exception;

import java.net.HttpURLConnection;

import okhttp3.Response;

public class ConflictException extends HttpException {

    public ConflictException(Response response) {
        super(response);
    }

    public ConflictException(String message) {
        super(HttpURLConnection.HTTP_CONFLICT, message);
    }

}
