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
import at.bitfire.dav4jvm.ktor.exception.DavException.Companion.MAX_EXCERPT_SIZE
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.request
import io.ktor.content.ByteArrayContent
import io.ktor.content.TextContent
import io.ktor.http.ContentType
import io.ktor.http.charset
import io.ktor.http.contentType
import io.ktor.utils.io.readBuffer
import kotlinx.coroutines.runBlocking
import kotlinx.io.Buffer
import kotlinx.io.EOFException
import kotlinx.io.readByteArray
import kotlinx.io.readString
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.ByteArrayOutputStream
import java.io.StringReader
import javax.annotation.WillNotClose
import kotlin.math.min

internal class HttpResponseInfo private constructor(
    val statusCode: Int,
    val requestExcerpt: String?,
    val responseExcerpt: String?,
    val errors: List<Error>
) {

    companion object {

        fun fromResponse(@WillNotClose response: HttpResponse): HttpResponseInfo {

            // extract request body if it's text
            val request = response.request
            val requestExcerptBuilder = StringBuilder(
                "${request.method} ${request.url}"
            )
            request.content.let { requestBody ->
                requestBody.contentType?.let { contentType ->
                    if (contentType.isText()) {
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
                        requestExcerptBuilder.append("\n\n<request body (${requestBody.contentLength} bytes)>")
                }
            }
            //requestExcerpt = requestExcerptBuilder.toString()

            // extract response body if it's text
            val responseBody = response.contentType()?.let { mimeType ->
                if (mimeType.isText())
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
            }

            // get XML errors from request body excerpt
            val errors: List<Error> = if (response.contentType()?.isXml() == true && responseBody != null)
                extractErrors(responseBody)
            else
                emptyList()

            return HttpResponseInfo(
                statusCode = response.status.value,
                requestExcerpt = requestExcerptBuilder.toString(),
                responseExcerpt = responseBody,
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
                        if (parser.propertyName() == at.bitfire.dav4jvm.ktor.Error.NAME)
                            return at.bitfire.dav4jvm.ktor.Error.parseError(parser)
                    eventType = parser.next()
                }
            } catch (_: XmlPullParserException) {
                // Couldn't parse XML, either invalid or maybe it wasn't even XML
            } catch (_: EOFException) {
                // Couldn't parse XML, either invalid or maybe it wasn't even XML
            }

            return emptyList()
        }


        // extensions

        fun ContentType.isText() =
            this.match(ContentType.Text.Any)
                    || this.match(ContentType.Application.Xml)
                    || (this.contentType == ContentType.Application.TYPE && this.contentSubtype == ContentType.Text.Html.contentSubtype)

        fun ContentType.isXml() =
            this.match(ContentType.Text.Xml)
                    || this.match(ContentType.Application.Xml)

    }

}