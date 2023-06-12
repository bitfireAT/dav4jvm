/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm.exception

import at.bitfire.dav4jvm.Dav4jvm
import at.bitfire.dav4jvm.Error
import at.bitfire.dav4jvm.XmlUtils
import io.ktor.client.call.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.utils.io.*
import io.ktor.utils.io.bits.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.errors.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import nl.adaptivity.xmlutil.EventType
import nl.adaptivity.xmlutil.XmlException
import nl.adaptivity.xmlutil.XmlSerializable
import nl.adaptivity.xmlutil.XmlStreaming
import kotlin.coroutines.CoroutineContext
import kotlin.jvm.JvmOverloads

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
        httpResponse: HttpResponse? = null
): Exception(message, ex), CoroutineScope {

    companion object {

        const val MAX_EXCERPT_SIZE = 10*1024   // don't dump more than 20 kB

        fun isPlainText(type: ContentType) =
                type.match(ContentType.Text.Any) ||
                (type.contentType == "application" && type.contentSubtype in arrayOf("html", "xml"))

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
                request = httpResponse.request.toString()

                httpResponse.request.content.let { body ->
                    body.contentType?.let { type ->
                        if (isPlainText(type)) {
                            requestBody = if(body is OutgoingContent.ByteArrayContent){
                                val bytes = body.bytes()
                                io.ktor.utils.io.core.String(bytes, charset = Charsets.UTF_8)
                            } else {
                                "Unknown outgoing content ${body::class.simpleName}"
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Dav4jvm.log.warn("Couldn't read HTTP request", e)
                requestBody = "Couldn't read HTTP request: ${e.message}"
            }

            try {
                //TODO see if this actually works
                launch {
                    // save response body excerpt
                    if (httpResponse.bodyAsChannel().availableForRead > 0) {
                        // response body has a source
                        val contentType = httpResponse.contentType()
                        when {
                            contentType == null -> {

                            }
                            isPlainText(contentType) -> {
                                responseBody = httpResponse.bodyAsText()
                            }
                            contentType.match(ContentType.Application.Xml) || contentType.match(ContentType.Text.Xml) -> {
                                try {
                                    val parser = XmlStreaming.newReader(httpResponse.bodyAsText())

                                    var eventType = parser.eventType
                                    while (eventType != EventType.END_DOCUMENT) {
                                        if (eventType == EventType.START_ELEMENT && parser.depth == 1)
                                            if (parser.name == Error.NAME)
                                                errors = Error.parseError(parser)
                                        eventType = parser.next()
                                    }
                                } catch (e: XmlException) {
                                    Dav4jvm.log.warn("Couldn't parse XML response", e)
                                }
                            }
                        }
                    }
                }
            } catch (e: IOException) {
                Dav4jvm.log.warn("Couldn't read HTTP response", e)
                responseBody = "Couldn't read HTTP response: ${e.message}"
            }
        } else
            response = null
    }

    override val coroutineContext: CoroutineContext = GlobalScope.coroutineContext

}