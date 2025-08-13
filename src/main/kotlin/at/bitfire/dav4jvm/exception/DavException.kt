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
import at.bitfire.dav4jvm.XmlUtils
import at.bitfire.dav4jvm.XmlUtils.propertyName
import okhttp3.MediaType
import okhttp3.Response
import okio.Buffer
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.ByteArrayOutputStream
import java.io.StringReader
import javax.annotation.WillNotClose

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
open class DavException @JvmOverloads constructor(
    message: String? = null,
    cause: Throwable? = null,
    statusCode: Int? = null,
    requestExcerpt: String? = null,
    responseExcerpt: String? = null,
    errors: List<Error> = emptyList()
): Exception(message, cause) {

    var statusCode: Int? = statusCode
        private set

    var requestExcerpt: String? = requestExcerpt
        private set

    var responseExcerpt: String? = responseExcerpt
        private set

    var errors: List<Error> = errors
        private set

    /**
     * Takes the request, response and errors from a given HTTP response.
     *
     * @param response  response to extract status code and request/response excerpt from (if possible)
     * @param message   optional exception message
     * @param cause     optional exception cause
     */
    constructor(
        message: String?,
        @WillNotClose response: Response,
        cause: Throwable? = null
    ) : this(message, cause) {
        // extract status code
        statusCode = response.code

        // extract request body
        val request = response.request
        request.body?.let { requestBody ->
            // Unfortunately doesn't have a size limit.
            // However large bodies are usually streaming/one-shot away.
            val buffer = Buffer()
            requestBody.writeTo(buffer)

            val baos = ByteArrayOutputStream()
            buffer.writeTo(baos)
            requestExcerpt = "${request.method} ${request.url}\n\n$baos"
        }

        // extract response body if response is plain text
        val mimeType = response.body.contentType()
        val responseBody =
            if (mimeType?.isPlainText() == true)
                try {
                    response.peekBody(MAX_EXCERPT_SIZE.toLong()).string()
                } catch (_: Exception) {
                    // response body not available anymore, probably already consumed / closed
                    null
                }
            else
                null
        responseExcerpt = responseBody

        // get XML errors from request body excerpt
        if (mimeType?.isXml() == true && responseBody != null)
            errors = extractErrors(responseBody)
    }

    private fun extractErrors(xml: String): List<Error> {
        try {
            val parser = XmlUtils.newPullParser()
            parser.setInput(StringReader(xml))

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.depth == 1)
                    if (parser.propertyName() == Error.NAME)
                        return Error.parseError(parser)
                eventType = parser.next()
            }
        } catch (_: XmlPullParserException) {
            // Couldn't parse XML, either invalid or maybe it wasn't even XML
        }

        return emptyList()
    }


    companion object {

        /**
         * maximum size of extracted response body
         */
        const val MAX_EXCERPT_SIZE = 20*1024

        private fun MediaType.isPlainText() =
            type == "text" ||
            (type == "application" && subtype in arrayOf("html", "xml"))

        private fun MediaType.isXml() =
            type in arrayOf("application", "text") && subtype == "xml"

    }

}