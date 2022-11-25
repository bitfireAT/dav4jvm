/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm

import org.kobjects.ktxml.api.EventType
import org.kobjects.ktxml.mini.MiniXmlPullParser
import org.kobjects.ktxml.api.XmlPullParserException
import java.io.IOException

object XmlUtils {

    const val NS_WEBDAV = "DAV:"
    const val NS_CALDAV = "urn:ietf:params:xml:ns:caldav"
    const val NS_CARDDAV = "urn:ietf:params:xml:ns:carddav"
    const val NS_APPLE_ICAL = "http://apple.com/ns/ical/"
    const val NS_CALENDARSERVER = "http://calendarserver.org/ns/"

    // New KXMLSerializer
    fun newSerializer() = KXmlSerializer()

    @Throws(IOException::class, XmlPullParserException::class)
    fun processTag(parser: MiniXmlPullParser, name: Property.Name, processor: () -> Unit) {
        val depth = parser.depth
        var eventType = parser.eventType
        while (!((eventType == EventType.END_TAG || eventType == EventType.END_DOCUMENT) && parser.depth == depth)) {
            if (eventType == EventType.START_TAG && parser.depth == depth + 1 && parser.propertyName() == name)
                processor()
            eventType = parser.next()
        }
    }

    @Throws(IOException::class, XmlPullParserException::class)
    fun readText(parser: MiniXmlPullParser): String? {
        var text: String? = null

        val depth = parser.depth
        var eventType = parser.eventType
        while (!(eventType == EventType.END_TAG && parser.depth == depth)) {
            if (eventType == EventType.TEXT && parser.depth == depth)
                text = parser.text
            eventType = parser.next()
        }

        return text
    }

    @Throws(IOException::class, XmlPullParserException::class)
    fun readTextProperty(parser: MiniXmlPullParser, name: Property.Name): String? {
        val depth = parser.depth
        var eventType = parser.eventType
        var result: String? = null
        while (!((eventType == EventType.END_TAG || eventType == EventType.END_DOCUMENT) && parser.depth == depth)) {
            if (eventType == EventType.START_TAG && parser.depth == depth + 1 && parser.propertyName() == name)
                result = parser.nextText()
            eventType = parser.next()
        }
        return result
    }

    @Throws(IOException::class, XmlPullParserException::class)
    fun readTextPropertyList(parser: MiniXmlPullParser, name: Property.Name, list: MutableCollection<String>) {
        val depth = parser.depth
        var eventType = parser.eventType
        while (!((eventType == EventType.END_TAG || eventType == EventType.END_DOCUMENT) && parser.depth == depth)) {
            if (eventType == EventType.START_TAG && parser.depth == depth + 1 && parser.propertyName() == name)
                list.add(parser.nextText())
            eventType = parser.next()
        }
    }


    fun KXmlSerializer.insertTag(name: Property.Name, contentGenerator: KXmlSerializer.() -> Unit = {}) {
        startTag(name.namespace, name.name)
        contentGenerator(this)
        endTag(name.namespace, name.name)
    }

    fun MiniXmlPullParser.propertyName(): Property.Name {
        val propNs = namespace
        val propName = name
        return Property.Name(propNs, propName)
    }

}
