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
import at.bitfire.dav4jvm.exception.DavException.Companion.MAX_EXCERPT_SIZE
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.readRawBytes
import io.ktor.client.statement.request
import io.ktor.http.ContentType
import io.ktor.http.charset
import io.ktor.http.content.ByteArrayContent
import io.ktor.http.content.TextContent
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import okio.Buffer
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStreamReader
import java.io.Serializable
import java.lang.Long.min
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Signals that an error occurred during a WebDAV-related operation.
 *
 * This could be a logical error like when a required ETag has not been
 * received, but also an explicit HTTP error.
 */
open class DavException @JvmOverloads constructor(
        message: String,
        ex: Throwable? = null,

        /**
         * An associated HTTP [HttpResponse]. Will be closed after evaluation.
         */
        httpResponse: HttpResponse? = null
): Exception(message, ex), Serializable {

    companion object {

        const val MAX_EXCERPT_SIZE = 10*1024   // don't dump more than 20 kB

        fun isPlainText(type: ContentType) =
                type.match(ContentType.Text.Any)
                        || type.match(ContentType.Application.Xml)
                        || (type.contentType == ContentType.Application.TYPE && type.contentSubtype == ContentType.Text.Html.contentSubtype)  // not sure if this is a valid case. TODO: Review with Ricki

    }

    private val logger
        get() = Logger.getLogger(javaClass.name)

    var request: String? = null

    /**
     * Body excerpt of [request] (up to [MAX_EXCERPT_SIZE] characters). Only available
     * if the HTTP request body was textual content and could be read again.
     */
    var requestBody: String? = null
        private set

    val response: String?

    /**
     * Body excerpt of [response] (up to [MAX_EXCERPT_SIZE] characters). Only available
     * if the HTTP response body was textual content.
     */
    var responseBody: String? = null
        private set

    /**
     * Precondition/postcondition XML elements which have been found in the XML response.
     */
    @Transient
    var errors: List<Error> = listOf()
        private set


    init {
        if (httpResponse != null) {
            response = httpResponse.toString() // Or a more custom string representation

            // TODO: Review AI Info and general implementation with Ricki
            //--- Request Body Handling (More Complex with Ktor from Response) ---
            // Ktor's HttpResponse doesn't directly hold a reference to the full original
            // request body in the same way OkHttp's Response can link back to its Request.
            // You'd typically capture the request body *before* sending the request
            // and pass it to this exception if needed.
            // For simplicity, I'm omitting the direct Ktor equivalent here,
            // assuming you might pass `requestBodyContent` to the constructor.
            // If you need to reconstruct parts of the request from httpResponse.request,
            // that's possible but limited.

            try {
                request = httpResponse.request.toString()

                httpResponse.request.content.let { body ->
                    body.contentType?.let { type ->
                        if (isPlainText(type)) {
                            val requestBodyBytes = when (body) {
                                is TextContent -> body.text.toByteArray(type.charset() ?: Charsets.UTF_8)
                                is ByteArrayContent -> body.bytes()
                                else -> body.toString().toByteArray(type.charset() ?: Charsets.UTF_8) // Fallback, may not be ideal
                            }
                            val buffer = Buffer()
                            buffer.write(requestBodyBytes)

                            val baos = ByteArrayOutputStream()
                            buffer.writeTo(baos, min(buffer.size, MAX_EXCERPT_SIZE.toLong()))

                            requestBody = baos.toString()
                        }
                    }
                }
            } catch (e: Exception) {
                logger.log(Level.WARNING, "Couldn't read HTTP request", e)
                requestBody = "Couldn't read HTTP request: ${e.message}"
            }

            try {
                // --- Response Body Handling ---
                // Ktor response bodies are typically consumed on read.
                // To get an excerpt and parse XML, you might need to read it once.
                // Be mindful of large responses.
                val responseBytes = runBlocking { httpResponse.readRawBytes() } // Read the whole body

                httpResponse.contentType()?.let { mimeType ->
                    if (isPlainText(mimeType)) {
                        val charset = mimeType.charset() ?: Charsets.UTF_8
                        responseBody = String(
                            responseBytes,
                            0,
                            min(responseBytes.size.toLong(), MAX_EXCERPT_SIZE.toLong()).toInt(),
                            charset
                        )
                    }

                    if (mimeType.contentType == "application" && mimeType.contentSubtype == "xml" ||
                        mimeType.contentType == "text" && mimeType.contentSubtype == "xml") {
                        try {
                            val parser = XmlUtils.newPullParser()
                            // Use the already read bytes
                            parser.setInput(InputStreamReader(ByteArrayInputStream(responseBytes), mimeType.charset() ?: Charsets.UTF_8))

                            var eventType = parser.eventType
                            while (eventType != XmlPullParser.END_DOCUMENT) {
                                if (eventType == XmlPullParser.START_TAG && parser.depth == 1) {
                                    if (parser.propertyName() == Error.NAME) {
                                        errors = Error.parseError(parser)
                                    }
                                }
                                eventType = parser.next()
                            }
                        } catch (e: XmlPullParserException) {
                            logger.log(Level.WARNING, "Couldn't parse XML response", e)
                        } catch (e: IOException) { // Catch IOException from parser.setInput
                            logger.log(Level.WARNING, "Couldn't set input for XML parser", e)
                        }
                    }
                }
            } catch (e: Exception) { // Broader catch for Ktor's body reading/statement exceptions
                logger.log(Level.WARNING, "Couldn't read Ktor HTTP response", e)
                responseBody = "Couldn't read Ktor HTTP response: ${e.message}"
            }
            // Ktor's HttpResponse body is typically managed by its scope,
            // no explicit close needed here like OkHttp's response.body.close()
        } else {
            response = null
        }
    }

}