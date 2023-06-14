/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm

import at.bitfire.dav4jvm.exception.InvalidPropertyException
import io.ktor.utils.io.errors.*
import nl.adaptivity.xmlutil.*
import nl.adaptivity.xmlutil.core.KtXmlWriter
import nl.adaptivity.xmlutil.core.XmlVersion

object XmlUtils {

    const val NS_WEBDAV = "DAV:"
    const val NS_CALDAV = "urn:ietf:params:xml:ns:caldav"
    const val NS_CARDDAV = "urn:ietf:params:xml:ns:carddav"
    const val NS_APPLE_ICAL = "http://apple.com/ns/ical/"
    const val NS_CALENDARSERVER = "http://calendarserver.org/ns/"

    fun createReader(source: String) = XmlStreaming.newGenericReader(source).also { /*Initialize*/ it.next() }
    fun createWriter(destination: Appendable) = KtXmlWriter(
        destination,
        isRepairNamespaces = true,
        xmlDeclMode = XmlDeclMode.Auto,
        xmlVersion = XmlVersion.XML10
    )

    @Throws(IOException::class, XmlException::class)
    fun processTag(
        parser: XmlReader,
        name: QName? = null,
        eventType: EventType = EventType.START_ELEMENT,
        targetDepth: Int = parser.depth + 1,
        processor: () -> Unit
    ) = processTag(parser, { d, e, n -> d == targetDepth && e == eventType && (name == null || name == n) }, processor)

    fun processTag(
        parser: XmlReader,
        selector: (depth: Int, eventType: EventType, name: QName?) -> Boolean,
        processor: () -> Unit
    ) {
        if (!parser.isStarted) parser.next()
        val endTagDepth = parser.depth
        var mEventType = parser.eventType
        if (mEventType != EventType.START_ELEMENT && mEventType != EventType.START_DOCUMENT) throw XmlException("Need to be at the start of a tag or document to process it! Was $mEventType")
        val processingDoc = mEventType == EventType.START_DOCUMENT
        val endTagName: QName? = if (!processingDoc) parser.name else null
        var cName = if (!processingDoc) parser.name else null
        do {
            if (selector(parser.depth, mEventType, cName)) {
                processor()
            }
            mEventType = parser.next()
            cName = when (mEventType) {
                EventType.END_ELEMENT, EventType.START_ELEMENT, EventType.ENTITY_REF -> parser.name
                else -> null
            }
        } while (
            !((mEventType == EventType.END_ELEMENT && parser.name == endTagName && parser.depth <= endTagDepth) ||
                    (processingDoc && mEventType == EventType.END_DOCUMENT))
        )
    }

    @Throws(IOException::class, XmlException::class)
    fun readText(parser: XmlReader): String? {
        var text: String? = null
        val cDepth = parser.depth
        processTag(parser, { d, e, _ -> d == cDepth && (e == EventType.TEXT || e == EventType.CDSECT) }) {
            text = parser.text
        }

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

        if (name.namespaceURI == XMLConstants.XML_NS_URI || name.namespaceURI == XMLConstants.XMLNS_ATTRIBUTE_NS_URI) {
            val namespace = namespaceContext.getNamespaceURI(name.prefix) ?: XMLConstants.NULL_NS_URI
            startTag(namespace, name.localPart, name.prefix)
        } else {
            var writeNs = false

            val usedPrefix = getPrefix(name.namespaceURI) ?: run {
                val currentNs = getNamespaceUri(name.prefix) ?: XMLConstants.NULL_NS_URI
                if (name.namespaceURI != currentNs) {
                    writeNs = true
                }
                if (name.prefix != XMLConstants.DEFAULT_NS_PREFIX) name.prefix else generateAutoPrefix()
            }
            startTag(name.namespaceURI, name.localPart, usedPrefix)
            if (writeNs) this.namespaceAttr(usedPrefix, name.namespaceURI)
        }

        contentGenerator(this)
        endTag(name)
    }

    private fun XmlWriter.generateAutoPrefix(): String {
        var prefix: String
        var prefixN = 1
        do {
            prefix = "n${prefixN++}"
        } while (getNamespaceUri(prefix) != null)
        return prefix
    }

    @Throws(XmlException::class)
    fun XmlReader.nextText(): String {
        require(EventType.START_ELEMENT, null)
        return when (next()) {
            EventType.TEXT, EventType.CDSECT -> {
                val rText = text
                if (next() != EventType.END_ELEMENT) throw XmlException()
                rText
            }

            EventType.END_ELEMENT -> ""
            else -> throw XmlException()
        }
    }

}
