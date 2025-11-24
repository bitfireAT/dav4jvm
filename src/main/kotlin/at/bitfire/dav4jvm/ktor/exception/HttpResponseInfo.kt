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
import at.bitfire.dav4jvm.XmlUtils
import at.bitfire.dav4jvm.XmlUtils.propertyName
import at.bitfire.dav4jvm.ktor.isText
import at.bitfire.dav4jvm.ktor.isXml
import at.bitfire.dav4jvm.property.webdav.WebDAV
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.request
import io.ktor.http.HttpStatusCode
import io.ktor.http.charset
import io.ktor.http.content.OutgoingContent
import io.ktor.http.content.TextContent
import io.ktor.http.contentType
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readBuffer
import kotlinx.io.EOFException
import kotlinx.io.readString
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.StringReader
import kotlin.math.min

internal class HttpResponseInfo private constructor(
    val status: HttpStatusCode,
    val requestExcerpt: String?,
    val responseExcerpt: String?,
    val errors: List<Error>
) {

    companion object {

        /**
         * maximum size of extracted response body
         */
        const val MAX_EXCERPT_SIZE = 20*1024

        suspend fun fromResponse(response: HttpResponse, responseBodyChannel: ByteReadChannel? = null): HttpResponseInfo {
            val request = response.request
            val requestExcerptBuilder = StringBuilder("${request.method} ${request.url}")

            val requestContent = request.content
            val requestContentType = requestContent.contentType
            val requestContentLength = requestContent.contentLength

            // extract request body if it's consumable text
            if (requestContent is TextContent) {
                val excerpt = requestContent.text.take(MAX_EXCERPT_SIZE)
                requestExcerptBuilder
                    .append("\n\n")
                    .append(excerpt)

            } else if (requestContent is OutgoingContent.ByteArrayContent && requestContentType != null && requestContentType.isText()) {
                val bytes = requestContent.bytes()
                val excerptSize = min(bytes.size, MAX_EXCERPT_SIZE)
                val truncated = bytes.copyOf(excerptSize)
                val excerpt = truncated.toString(requestContentType.charset() ?: Charsets.UTF_8)
                requestExcerptBuilder
                    .append("\n\n")
                    .append(excerpt)

            } else if (requestContentLength != null && requestContentLength > 0) {
                // otherwise, at least indicate request body size
                requestExcerptBuilder.append("\n\n<request body with $requestContentLength byte(s)>")
            }

            // extract response body if it's text
            val responseContentType = response.contentType()
            val responseExcerpt: String? =
                if (responseContentType != null && responseContentType.isText()) {
                    try {
                        val responseBody = responseBodyChannel ?: response.bodyAsChannel()
                        val charset = responseContentType.charset() ?: Charsets.UTF_8
                        responseBody.readBuffer(MAX_EXCERPT_SIZE).readString(charset)
                    } catch (_: Exception) {
                        // response body not available anymore, probably already consumed
                        null
                    }
                } else
                    null

            // extract XML errors from response body excerpt
            val errors: List<Error> =
                if (responseContentType != null && responseContentType.isXml() && responseExcerpt != null)
                    extractErrors(responseExcerpt)
                else
                    emptyList()

            return HttpResponseInfo(
                status = response.status,
                requestExcerpt = requestExcerptBuilder.toString(),
                responseExcerpt = responseExcerpt,
                errors = errors
            )
        }

        private fun extractErrors(xml: String): List<Error> {
            try {
                val parser = XmlUtils.newPullParser()
                parser.setInput(StringReader(xml))

                var eventType = parser.eventType
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.START_TAG && parser.depth == 1)
                        if (parser.propertyName() == WebDAV.Error)
                            return Error.parseError(parser)
                    eventType = parser.next()
                }
            } catch (_: XmlPullParserException) {
                // Couldn't parse XML, either invalid or maybe it wasn't even XML
            } catch (_: EOFException) {
                // Couldn't parse XML, either invalid or maybe it wasn't even XML
            }

            return emptyList()
        }

    }

}