/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.dav4android.exception

import okhttp3.Response
import java.net.HttpURLConnection

class ConflictException: HttpException {

    constructor(response: Response): super(response)
    constructor(message: String?): super(HttpURLConnection.HTTP_CONFLICT, message)

}
