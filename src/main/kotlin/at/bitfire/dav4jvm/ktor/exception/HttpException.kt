/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

package at.bitfire.dav4jvm.ktor.exception

import at.bitfire.dav4jvm.Error
import io.ktor.client.statement.HttpResponse

/**
 * Signals that a HTTP error was sent by the server in the context of a WebDAV operation.
 */
class HttpException(
    message: String? = null,
    cause: Throwable? = null,
    override val statusCode: Int,
    requestExcerpt: String?,
    responseExcerpt: String?,
    errors: List<Error> = emptyList()
): DavException(message, cause, statusCode, requestExcerpt, responseExcerpt, errors) {

    // status code classes

    /** Whether the [statusCode] is 3xx and thus indicates a redirection. */
    val isRedirect
        get() = statusCode / 100 == 3

    /** Whether the [statusCode] is 4xx and thus indicates a client error. */
    val isClientError
        get() = statusCode / 100 == 4

    /** Whether the [statusCode] is 5xx and thus indicates a server error. */
    val isServerError
        get() = statusCode / 100 == 5


    companion object {

        /**
         * Creates a [HttpException] from the request, response and errors of a given HTTP response.
         *
         * @param response  unconsumed response to extract status code and request/response excerpt from (if possible)
         * @param message   optional exception message
         * @param cause     optional exception cause
         */
        suspend fun fromResponse(
            response: HttpResponse,
            message: String = "HTTP ${response.status}",
            cause: Throwable? = null
        ): HttpException {
            val responseInfo = HttpResponseInfo.fromResponse(response)
            return HttpException(
                message = message,
                cause = cause,
                statusCode = responseInfo.statusCode,
                requestExcerpt = responseInfo.requestExcerpt,
                responseExcerpt = responseInfo.responseExcerpt,
                errors = responseInfo.errors
            )
        }

    }

}