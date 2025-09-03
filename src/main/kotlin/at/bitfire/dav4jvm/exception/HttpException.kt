/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

package at.bitfire.dav4jvm.exception

import at.bitfire.dav4jvm.Error
import okhttp3.Response
import javax.annotation.WillNotClose

/**
 * Signals that a HTTP error was sent by the server in the context of a WebDAV operation.
 */
open class HttpException(
    message: String? = null,
    cause: Throwable? = null,
    override val statusCode: Int,
    requestExcerpt: String?,
    responseExcerpt: String?,
    errors: List<Error> = emptyList()
): DavException(message, cause, statusCode, requestExcerpt, responseExcerpt, errors) {

    // constructor from Response

    internal constructor(
        httpResponseInfo: HttpResponseInfo,
        message: String?,
        cause: Throwable? = null
    ): this(
        message = message,
        cause = cause,
        statusCode = httpResponseInfo.statusCode,
        requestExcerpt = httpResponseInfo.requestExcerpt,
        responseExcerpt = httpResponseInfo.responseExcerpt,
        errors = httpResponseInfo.errors
    )

    /**
     * Takes the request, response and errors from a given HTTP response.
     *
     * @param response  response to extract status code and request/response excerpt from (if possible)
     * @param message   optional exception message
     * @param cause     optional exception cause
     */
    constructor(
        @WillNotClose response: Response,
        message: String = "HTTP ${response.code} ${response.message}",
        cause: Throwable? = null
    ) : this(
        message = message,
        cause = cause,
        httpResponseInfo = HttpResponseInfo.fromResponse(response)
    )


}