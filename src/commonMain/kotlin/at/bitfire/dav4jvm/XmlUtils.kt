/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm

import at.bitfire.dav4jvm.exception.InvalidPropertyException
import io.ktor.utils.io.errors.*
import nl.adaptivity.xmlutil.*

object XmlUtils {

    const val NS_WEBDAV = "DAV:"
    const val NS_CALDAV = "urn:ietf:params:xml:ns:caldav"
    const val NS_CARDDAV = "urn:ietf:params:xml:ns:carddav"
    const val NS_APPLE_ICAL = "http://apple.com/ns/ical/"
    const val NS_CALENDARSERVER = "http://calendarserver.org/ns/"

    fun createReader(source: String) = XmlStreaming.newReader(source).also { /*Initialize*/ it.next() }
    fun createWriter(destination: Appendable) =
        XmlStreaming.newWriter(destination, repairNamespaces = true, xmlDeclMode = XmlDeclMode.Auto)

    @Throws(IOException::class, XmlException::class)
    fun processTag(
        parser: XmlReader,
        name: QName? = null,
        eventType: EventType = EventType.START_ELEMENT,
        depthIncrement: Int = 1,
        processor: () -> Unit
    ) {
        if (!parser.isStarted) parser.next()
        val targetDepth = parser.depth + depthIncrement
        val endTagDepth = parser.depth
        var mEventType = parser.eventType
        if (mEventType != EventType.START_ELEMENT && mEventType != EventType.START_DOCUMENT) throw XmlException("Need to be at the start of a tag or document to process it! Was $mEventType")
        val processingDoc = mEventType == EventType.START_DOCUMENT
        val endTagName: QName? = if (!processingDoc) parser.name else null
        do {
            if (parser.depth == targetDepth && mEventType == eventType && (name == null || parser.name == name)) {
                processor()
            }
            mEventType = parser.next()
        } while (
            !((mEventType == EventType.END_ELEMENT && parser.name == endTagName && parser.depth <= endTagDepth) ||
                    (processingDoc && mEventType == EventType.END_DOCUMENT))
        )
    }

    @Throws(IOException::class, XmlException::class)
    fun readText(parser: XmlReader): String? {
        var text: String? = null

        processTag(parser, eventType = EventType.TEXT, depthIncrement = 0) { text = parser.text }

        return text
    }

    /**
     * Same as [readText], but requires a [XmlPullParser.TEXT] value.
     *
     * @throws InvalidPropertyException when no text could be read
     */
    @Throws(InvalidPropertyException::class, IOException::class, XmlException::class)
    fun requireReadText(parser: XmlReader): String =
        readText(parser)
            ?: throw InvalidPropertyException("XML text for ${parser.namespaceURI}:${parser.name} must not be empty")

    @Throws(IOException::class, XmlException::class)
    fun readTextProperty(parser: XmlReader, name: QName): String? {
        var result: String? = null
        processTag(parser, name) { result = parser.nextText() }
        return result
    }

    @Throws(IOException::class, XmlException::class)
    fun readTextPropertyList(parser: XmlReader, name: QName, list: MutableCollection<String>) {
        processTag(parser, name) { list.add(parser.nextText()) }
    }


    fun XmlWriter.insertTag(name: QName, contentGenerator: XmlWriter.() -> Unit = {}) {
        smartStartTag(name)
        contentGenerator(this)
        endTag(name)
    }

    @Throws(XmlException::class)
    fun XmlReader.nextText(): String {
        require(EventType.START_ELEMENT, null)
        return when (next()) {
            EventType.TEXT -> {
                val rText = text
                if (next() != EventType.END_ELEMENT) throw XmlException()
                rText
            }

            EventType.END_ELEMENT -> ""
            else -> throw XmlException()
        }
    }

}
