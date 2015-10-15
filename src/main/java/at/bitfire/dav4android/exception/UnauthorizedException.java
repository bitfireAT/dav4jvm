/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.dav4android.exception;

import com.squareup.okhttp.Response;

public class UnauthorizedException extends HttpException {

    public UnauthorizedException(Response response) {
        super(response);
    }

    public UnauthorizedException(String message) {
        super(401, message);
    }

}
