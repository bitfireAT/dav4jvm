/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException

object XmlUtils {

    const val NS_WEBDAV = "DAV:"
    const val NS_CALDAV = "urn:ietf:params:xml:ns:caldav"
    const val NS_CARDDAV = "urn:ietf:params:xml:ns:carddav"
    const val NS_APPLE_ICAL = "http://apple.com/ns/ical/"
    const val NS_CALENDARSERVER = "http://calendarserver.org/ns/"

    private val factory: XmlPullParserFactory
    init {
        try {
            factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
        } catch (e: XmlPullParserException) {
            throw RuntimeException("Couldn't create XmlPullParserFactory", e)
        }
    }

    fun newPullParser() = factory.newPullParser()!!
    fun newSerializer() = factory.newSerializer()!!


    @Throws(IOException::class, XmlPullParserException::class)
    fun processTag(parser: XmlPullParser, namespace: String, name: String, processor: () -> Unit) {
        val depth = parser.depth
        var eventType = parser.eventType
        while (!((eventType == XmlPullParser.END_TAG || eventType == XmlPullParser.END_DOCUMENT) && parser.depth == depth)) {
            if (eventType == XmlPullParser.START_TAG && parser.depth == depth + 1 &&
                    parser.namespace == namespace && parser.name == name)
                processor()
            eventType = parser.next()
        }
    }

    @Throws(IOException::class, XmlPullParserException::class)
    fun readText(parser: XmlPullParser): String? {
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

    @Throws(IOException::class, XmlPullParserException::class)
    fun readTextPropertyList(parser: XmlPullParser, name: Property.Name, list: MutableCollection<String>) {
        val depth = parser.depth
        var eventType = parser.eventType
        while (!((eventType == XmlPullParser.END_TAG || eventType == XmlPullParser.END_DOCUMENT) && parser.depth == depth)) {
            if (eventType == XmlPullParser.START_TAG && parser.depth == depth + 1 &&
                    Property.Name(parser.namespace, parser.name) == name)
                list.add(parser.nextText())
            eventType = parser.next()
        }
    }

}
