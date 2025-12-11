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
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode

/**
 * Signals that a HTTP error was sent by the server in the context of a WebDAV operation.
 */
open class HttpException internal constructor(
    status: HttpStatusCode,
    requestExcerpt: String?,
    responseExcerpt: String?,
    errors: List<Error> = emptyList()
): DavException(
    message = "HTTP ${status.value} ${reasonPhrase(status)}",
    statusCode = status.value,
    requestExcerpt = requestExcerpt,
    responseExcerpt = responseExcerpt,
    errors = errors
) {

    override val statusCode: Int = status.value

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
         *
         * @return specific HTTP response (like [UnauthorizedException] for 401) or generic
         * [HttpException] if no specific exception class is available
         */
        suspend fun fromResponse(response: HttpResponse): HttpException {
            val responseInfo = HttpResponseInfo.fromResponse(response)
            return when (responseInfo.status) {
                // specific status codes
                HttpStatusCode.Unauthorized ->          // 401 Unauthorized
                    UnauthorizedException(responseInfo)

                HttpStatusCode.Forbidden ->             // 403 Forbidden
                    ForbiddenException(responseInfo)

                HttpStatusCode.NotFound ->              // 404 Not Found
                    NotFoundException(responseInfo)

                HttpStatusCode.Conflict ->              // 409 Conflict
                    ConflictException(responseInfo)

                HttpStatusCode.Gone ->                  // 410 Gone
                    GoneException(responseInfo)

                HttpStatusCode.PreconditionFailed ->    // 412 Precondition failed
                    PreconditionFailedException(responseInfo)

                HttpStatusCode.ServiceUnavailable ->    // 503 Service Unavailable
                    ServiceUnavailableException(
                        responseInfo,
                        retryAfter = response.headers[HttpHeaders.RetryAfter]
                    )

                else ->     // generic HTTP exception
                    HttpException(
                        status = response.status,
                        requestExcerpt = responseInfo.requestExcerpt,
                        responseExcerpt = responseInfo.responseExcerpt,
                        errors = responseInfo.errors
                    )
            }
        }

        /**
         * Determines the reason phrase / description of the given HTTP status. If
         * there is none, a default phrase for the respective status code is returned.
         *
         * @param status    HTTP status
         *
         * @return reason phrase, either taken from [status] or default reason phrase for that code
         */
        fun reasonPhrase(status: HttpStatusCode): String {
            val description = status.description
            if (description.isNotBlank())
                return description

            // blank description (happens for instance when using HTTP/2), find default reason phrase
            val defaultStatus = HttpStatusCode.fromValue(status.value)
            return defaultStatus.description
        }

    }

}