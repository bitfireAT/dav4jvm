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

public class PreconditionFailedException extends HttpException {

    public PreconditionFailedException(Response response) {
        super(response);
    }

    public PreconditionFailedException(String message) {
        super(HttpURLConnection.HTTP_PRECON_FAILED, message);
    }

}
