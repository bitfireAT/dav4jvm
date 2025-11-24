/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

package at.bitfire.dav4jvm.okhttp.exception

import at.bitfire.dav4jvm.Error
import at.bitfire.dav4jvm.XmlUtils
import at.bitfire.dav4jvm.XmlUtils.propertyName
import at.bitfire.dav4jvm.okhttp.exception.DavException.Companion.MAX_EXCERPT_SIZE
import at.bitfire.dav4jvm.property.webdav.WebDAV
import okhttp3.MediaType
import okhttp3.Response
import okio.Buffer
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

        fun fromResponse(@WillNotClose response: Response): HttpResponseInfo {
            // extract request body if it's text
            val request = response.request
            val requestExcerptBuilder = StringBuilder(
                "${request.method} ${request.url}"
            )
            request.body?.let { requestBody ->
                if (requestBody.contentType()?.isText() == true) {
                    // Unfortunately Buffer doesn't have a size limit.
                    // However large bodies are usually streaming/one-shot away.
                    val buffer = Buffer()
                    requestBody.writeTo(buffer)

                    ByteArrayOutputStream().use { baos ->
                        buffer.writeTo(baos, min(buffer.size, MAX_EXCERPT_SIZE.toLong()))
                        requestExcerptBuilder
                            .append("\n\n")
                            .append(baos.toString())
                    }
                } else
                    requestExcerptBuilder.append("\n\n<request body (${requestBody.contentLength()} bytes)>")
            }

            // extract response body if it's text
            val mimeType = response.body.contentType()
            val responseBody =
                if (mimeType?.isText() == true)
                    try {
                        response.peekBody(MAX_EXCERPT_SIZE.toLong()).string()
                    } catch (_: Exception) {
                        // response body not available anymore, probably already consumed / closed
                        null
                    }
                else
                    null

            // get XML errors from request body excerpt
            val errors: List<Error> = if (mimeType?.isXml() == true && responseBody != null)
                extractErrors(responseBody)
            else
                emptyList()

            return HttpResponseInfo(
                statusCode = response.code,
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
                        if (parser.propertyName() == WebDAV.Error)
                            return Error.parseError(parser)
                    eventType = parser.next()
                }
            } catch (_: XmlPullParserException) {
                // Couldn't parse XML, either invalid or maybe it wasn't even XML
            }

            return emptyList()
        }


        // extensions

        private fun MediaType.isText() =
            type == "text" ||
                    (type == "application" && subtype in arrayOf("html", "xml"))

        private fun MediaType.isXml() =
            type in arrayOf("application", "text") && subtype == "xml"

    }

}