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
import io.ktor.utils.io.ByteReadChannel

/**
 * Signals that an error occurred during a WebDAV-related operation.
 *
 * This could be a logical error like when a required ETag has not been received, but also an explicit HTTP error
 * (usually with a subclass of [HttpException], which in turn extends this class).
 *
 * Often, HTTP response bodies contain valuable information about the error in text format (for instance, a HTML page
 * that contains details about the error) and/or as `<DAV:error>` XML elements. However, such response bodies
 * are sometimes very large.
 *
 * So, if possible and useful, a size-limited excerpt of the associated HTTP request and response can be
 * attached and subsequently included in application-level debug info or shown to the user.
 *
 * Note: [Exception] is serializable, so objects of this class must contain only serializable objects.
 *
 * @param statusCode        status code of associated HTTP response
 * @param requestExcerpt    cached excerpt of associated HTTP request body
 * @param responseExcerpt   cached excerpt of associated HTTP response body
 * @param errors            precondition/postcondition XML elements which have been found in the XML response
 */
open class DavException(
    message: String? = null,
    open val statusCode: Int? = null,
    val requestExcerpt: String? = null,
    val responseExcerpt: String? = null,
    val errors: List<Error> = emptyList(),
    cause: Throwable? = null
): Exception(message, cause) {

    companion object {

        /**
         * Creates a [DavException] from the request, response and errors of a given HTTP response.
         *
         * @param message               optional exception message
         * @param cause                 optional exception cause
         * @param response              response to extract status code and request/response excerpt from (if possible)
         * @param responseBodyChannel   optional existing response body channel that can be used to read the response body
         */
        suspend fun fromResponse(
            message: String,
            response: HttpResponse,
            responseBodyChannel: ByteReadChannel? = null,
            cause: Throwable? = null
        ): DavException {
            val responseInfo = HttpResponseInfo.fromResponse(response, responseBodyChannel)
            return DavException(
                message = message,
                cause = cause,
                statusCode = responseInfo.status.value,
                requestExcerpt = responseInfo.requestExcerpt,
                responseExcerpt = responseInfo.responseExcerpt,
                errors = responseInfo.errors
            )
        }

    }

}
