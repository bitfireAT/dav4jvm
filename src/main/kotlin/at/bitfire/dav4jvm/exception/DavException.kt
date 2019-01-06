/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm.exception

import at.bitfire.dav4jvm.Constants
import at.bitfire.dav4jvm.Error
import at.bitfire.dav4jvm.XmlUtils
import okhttp3.MediaType
import okhttp3.Response
import okio.Buffer
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.Serializable
import java.util.logging.Level

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
         * An associated HTTP [Response]. Will be closed after evaluation.
         */
        httpResponse: Response? = null
): Exception(message, ex), Serializable {

    companion object {

        const val MAX_EXCERPT_SIZE = 10*1024   // don't dump more than 20 kB

        fun isPlainText(type: MediaType) =
                type.type() == "text" ||
                (type.type() == "application" && type.subtype() in arrayOf("html", "xml"))

    }

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
    var errors: List<Error> = listOf()
        private set


    init {
        if (httpResponse != null) {
            response = httpResponse.toString()

            try {
                request = httpResponse.request().toString()

                httpResponse.request().body()?.let { body ->
                    body.contentType()?.let {
                        if (isPlainText(it)) {
                            val buffer = Buffer()
                            body.writeTo(buffer)
                            val baos = ByteArrayOutputStream()
                            buffer.writeTo(baos)
                            requestBody = baos.toString(it.charset(Charsets.UTF_8)!!.name())
                        }
                    }
                }
            } catch (e: Exception) {
                Constants.log.log(Level.WARNING, "Couldn't read HTTP request", e)
                requestBody = "Couldn't read HTTP request: ${e.message}"
            }

            try {
                // save response body excerpt
                if (httpResponse.body()?.source() != null) {
                    // response body has a source

                    httpResponse.peekBody(MAX_EXCERPT_SIZE.toLong()).let { body ->
                        body.contentType()?.let { mimeType ->
                            if (isPlainText(mimeType))
                                responseBody = body.string()
                        }
                    }

                    httpResponse.body()?.use { body ->
                        body.contentType()?.let {
                            if (it.type() in arrayOf("application", "text") && it.subtype() == "xml") {
                                // look for precondition/postcondition XML elements
                                try {
                                    val parser = XmlUtils.newPullParser()
                                    parser.setInput(body.charStream())

                                    var eventType = parser.eventType
                                    while (eventType != XmlPullParser.END_DOCUMENT) {
                                        if (eventType == XmlPullParser.START_TAG && parser.depth == 1)
                                            if (parser.namespace == XmlUtils.NS_WEBDAV && parser.name == "error")
                                                errors = Error.parseError(parser)
                                        eventType = parser.next()
                                    }
                                } catch (e: XmlPullParserException) {
                                    Constants.log.log(Level.WARNING, "Couldn't parse XML response", e)
                                }
                            }
                        }
                    }
                }
            } catch (e: IOException) {
                Constants.log.log(Level.WARNING, "Couldn't read HTTP response", e)
                responseBody = "Couldn't read HTTP response: ${e.message}"
            } finally {
                httpResponse.body()?.close()
            }
        } else
            response = null
    }

}