/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm

import at.bitfire.dav4jvm.exception.InvalidPropertyException
import io.ktor.utils.io.errors.*
import nl.adaptivity.xmlutil.*
import nl.adaptivity.xmlutil.core.impl.multiplatform.Reader

object XmlUtils {

    const val NS_WEBDAV = "DAV:"
    const val NS_CALDAV = "urn:ietf:params:xml:ns:caldav"
    const val NS_CARDDAV = "urn:ietf:params:xml:ns:carddav"
    const val NS_APPLE_ICAL = "http://apple.com/ns/ical/"
    const val NS_CALENDARSERVER = "http://calendarserver.org/ns/"


    @Throws(IOException::class, XmlException::class)
    fun processTag(parser: XmlReader, name: QName, processor: () -> Unit) {
        val depth = parser.depth
        var eventType = parser.eventType
        while (!((eventType == EventType.END_ELEMENT || eventType == EventType.END_DOCUMENT) && parser.depth == depth)) {
            if (eventType == EventType.START_ELEMENT && parser.depth == depth + 1 && parser.name == name)
                processor()
            eventType = parser.next()
        }
    }

    @Throws(IOException::class, XmlException::class)
    fun readText(parser: XmlReader): String? {
        var text: String? = null

        val depth = parser.depth
        var eventType = parser.eventType
        while (!(eventType == EventType.END_ELEMENT && parser.depth == depth)) {
            if (eventType == EventType.TEXT && parser.depth == depth)
                text = parser.text
            eventType = parser.next()
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
        readText(parser) ?:
        throw InvalidPropertyException("XML text for ${parser.namespaceURI}:${parser.name} must not be empty")

    @Throws(IOException::class, XmlException::class)
    fun readTextProperty(parser: XmlReader, name: QName): String? {
        val depth = parser.depth
        var eventType = parser.eventType
        var result: String? = null
        while (!((eventType == EventType.END_ELEMENT || eventType == EventType.END_DOCUMENT) && parser.depth == depth)) {
            if (eventType == EventType.START_ELEMENT && parser.depth == depth + 1 && parser.name == name)
                result = parser.nextText()
            eventType = parser.next()
        }
        return result
    }

    @Throws(IOException::class, XmlException::class)
    fun readTextPropertyList(parser: XmlReader, name: QName, list: MutableCollection<String>) {
        val depth = parser.depth
        var eventType = parser.eventType
        while (!((eventType == EventType.END_ELEMENT || eventType == EventType.END_DOCUMENT) && parser.depth == depth)) {
            if (eventType == EventType.START_ELEMENT && parser.depth == depth + 1 && parser.name == name)
                list.add(parser.nextText())
            eventType = parser.next()
        }
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
                if (next() != EventType.END_ELEMENT) throw  XmlException()
                rText
            }
            EventType.END_ELEMENT -> ""
            else -> throw XmlException()
        }
    }

}
