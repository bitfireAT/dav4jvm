/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

package at.bitfire.dav4jvm

import at.bitfire.dav4jvm.XmlUtils.propertyName
import at.bitfire.dav4jvm.property.caldav.SupportedCalendarData.Companion.CONTENT_TYPE
import at.bitfire.dav4jvm.property.caldav.SupportedCalendarData.Companion.VERSION
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException
import java.time.Instant
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Reads/processes XML tags which are used for WebDAV.
 *
 * @param parser The parser to read from.
 */
class XmlReader(
    private val parser: XmlPullParser
) {

    // base processing

    /**
     * Reads child elements of the current element. Whenever a direct child with the given name is found,
     * [processor] is called for each one.
     */
    @Throws(IOException::class, XmlPullParserException::class)
    fun processTag(name: Property.Name, processor: XmlReader.() -> Unit) {
        val depth = parser.depth
        var eventType = parser.eventType
        while (!((eventType == XmlPullParser.END_TAG || eventType == XmlPullParser.END_DOCUMENT) && parser.depth == depth)) {
            if (eventType == XmlPullParser.START_TAG && parser.depth == depth + 1 && parser.propertyName() == name)
                processor()
            eventType = parser.next()
        }
    }

    /**
     * Reads the inline text of the current element.
     *
     * For instance, if the parser is at the beginning of this XML:
     *
     * ```
     * <tag>text</tag>
     * ```
     *
     * this function will return "text".
     *
     * @return text or `null` if no text is found
     */
    @Throws(IOException::class, XmlPullParserException::class)
    fun readText(): String? {
        var text: String? = null

        val depth = parser.depth
        var eventType = parser.eventType
        while (!(eventType == XmlPullParser.END_TAG && parser.depth == depth)) {
            if (eventType == XmlPullParser.TEXT && parser.depth == depth)
                text = parser.text
            eventType = parser.next()
        }

        return text
    }

    /**
     * Reads child elements of the current element. When a direct child with the given name is found,
     * its text is returned.
     *
     * @param name The name of the tag to read.
     * @return The text inside the tag, or `null` if the tag is not found.
     */
    @Throws(IOException::class, XmlPullParserException::class)
    fun readTextProperty(name: Property.Name): String? {
        var result: String? = null

        val depth = parser.depth
        var eventType = parser.eventType
        while (!((eventType == XmlPullParser.END_TAG || eventType == XmlPullParser.END_DOCUMENT) && parser.depth == depth)) {
            if (eventType == XmlPullParser.START_TAG && parser.depth == depth + 1 && parser.propertyName() == name)
                result = parser.nextText()
            eventType = parser.next()
        }
        return result
    }

    /**
     * Reads child elements of the current element. Whenever a direct child with the given name is
     * found, its text is added to the given list.
     *
     * @param name The name of the tag to read.
     * @param list The list to add the text to.
     */
    @Throws(IOException::class, XmlPullParserException::class)
    fun readTextPropertyList(name: Property.Name, list: MutableCollection<String>) {
        val depth = parser.depth
        var eventType = parser.eventType
        while (!((eventType == XmlPullParser.END_TAG || eventType == XmlPullParser.END_DOCUMENT) && parser.depth == depth)) {
            if (eventType == XmlPullParser.START_TAG && parser.depth == depth + 1 && parser.propertyName() == name)
                list.add(parser.nextText())
            eventType = parser.next()
        }
    }


    // extended processing (uses readText etc.)

    /**
     * Uses [readText] to read the tag's value (which is expected to be in _HTTP-date_ format), and converts
     * it into an [Instant] using [HttpUtils.parseDate].
     *
     * If the conversion fails for any reason, null is returned, and a message is displayed in log.
     */
    fun readHttpDate(): Instant? {
        return readText()?.let { rawDate ->
            val date = HttpUtils.parseDate(rawDate)
            if (date != null)
                date
            else {
                val logger = Logger.getLogger(javaClass.name)
                logger.warning("Couldn't parse HTTP-date")
                null
            }
        }
    }

    /**
     * Uses [readText] to read the tag's value (which is expected to be a number), and converts it
     * into a [Long] with [String.toLong].
     *
     * If the conversion fails for any reason, null is returned, and a message is displayed in log.
     */
    fun readLong(): Long? {
        return readText()?.let { valueStr ->
            try {
                valueStr.toLong()
            } catch(e: NumberFormatException) {
                val logger = Logger.getLogger(javaClass.name)
                logger.log(Level.WARNING, "Couldn't parse as Long: $valueStr", e)
                null
            }
        }
    }

    /**
     * Processes all the tags named [tagName], and sends every tag that has the [CONTENT_TYPE]
     * attribute with [onNewType].
     *
     * @param tagName The name of the tag that contains the [CONTENT_TYPE] attribute value.
     * @param onNewType Called every time a new [MediaType] is found.
     */
    fun readContentTypes(tagName: Property.Name, onNewType: (MediaType) -> Unit) {
        try {
            processTag(tagName) {
                parser.getAttributeValue(null, CONTENT_TYPE)?.let { contentType ->
                    var type = contentType
                    parser.getAttributeValue(null, VERSION)?.let { version -> type += "; version=$version" }
                    type.toMediaTypeOrNull()?.let(onNewType)
                }
            }
        } catch(e: XmlPullParserException) {
            val logger = Logger.getLogger(javaClass.name)
            logger.log(Level.SEVERE, "Couldn't parse content types", e)
        }
    }

}