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

import at.bitfire.dav4jvm.ktor.Error
import at.bitfire.dav4jvm.ktor.XmlUtils
import at.bitfire.dav4jvm.ktor.XmlUtils.propertyName
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.request
import io.ktor.http.ContentType
import io.ktor.http.charset
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.content.TextContent
import io.ktor.http.contentType
import io.ktor.utils.io.readBuffer
import kotlinx.coroutines.runBlocking
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlinx.io.readString
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.ByteArrayOutputStream
import java.io.StringReader
import java.lang.Long.min


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
        response: HttpResponse,
        cause: Throwable? = null
    ) : this(message, cause) {
        // extract status code
        statusCode = response.status.value

        // extract request body if it's text
        val request = response.request
        val requestExcerptBuilder = StringBuilder(
            "${request.method} ${request.url}"
        )
        request.content.let { requestBody ->
            requestBody.contentType?.let { contentType ->
                if (isText(contentType)) {
                    val requestBodyBytes = when (requestBody) {
                        is TextContent -> requestBody.text.toByteArray(contentType.charset() ?: Charsets.UTF_8)
                        is ByteArrayContent -> requestBody.bytes()
                        else -> requestBody.toString().toByteArray(contentType.charset() ?: Charsets.UTF_8) // Fallback, may not be ideal
                    }
                    val buffer = Buffer()
                    buffer.write(requestBodyBytes)

                    val bytesToRead = min(buffer.size, MAX_EXCERPT_SIZE.toLong()).toInt()
                    val excerptBytes = buffer.readByteArray(bytesToRead)
                    val baos = ByteArrayOutputStream()
                    baos.write(excerptBytes)

                    requestExcerptBuilder
                        .append("\n\n")
                        .append(baos.toString())
                } else
                    requestExcerptBuilder.append("\n\n<request body>")
            }
        }
        requestExcerpt = requestExcerptBuilder.toString()

        // extract response body if it's text
        response.contentType()?.let { mimeType ->
            val responseBody =
                if (isText(mimeType))
                    try {
                        runBlocking {
                            response
                                .bodyAsChannel()
                                .readBuffer(MAX_EXCERPT_SIZE)
                                .readString(mimeType.charset() ?: Charsets.UTF_8)
                        }
                    } catch (_: Exception) {
                        // response body not available anymore, probably already consumed / closed
                        null
                    }
                else
                    null
            responseExcerpt = responseBody

            // get XML errors from request body excerpt
            if (isXml(mimeType) && responseBody?.isNotBlank() == true)
                errors = extractErrors(responseBody)

        }
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

        const val MAX_EXCERPT_SIZE = 20*1024   // don't dump more than 20 kB

        fun isText(type: ContentType) =
            type.match(ContentType.Text.Any)
                    || type.match(ContentType.Application.Xml)
                    || (type.contentType == ContentType.Application.TYPE && type.contentSubtype == ContentType.Text.Html.contentSubtype)  // not sure if this is a valid case. TODO: Review with Ricki

        fun isXml(type: ContentType) =
            type.match(ContentType.Text.Xml)
                    || type.match(ContentType.Application.Xml)

    }
}
