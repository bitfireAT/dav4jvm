/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.dav4android

import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import java.util.logging.Logger

open class DavCollection(
        httpClient: OkHttpClient,
        location: HttpUrl,
        log: Logger = Constants.log
): DavResource(httpClient, location, log)