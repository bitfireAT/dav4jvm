/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.dav4android

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException

object XmlUtils {

    @JvmField val NS_WEBDAV = "DAV:"
    @JvmField val NS_CALDAV = "urn:ietf:params:xml:ns:caldav"
    @JvmField val NS_CARDDAV = "urn:ietf:params:xml:ns:carddav"
    @JvmField val NS_APPLE_ICAL = "http://apple.com/ns/ical/"
    @JvmField val NS_CALENDARSERVER = "http://calendarserver.org/ns/"

    private val factory: XmlPullParserFactory
    init {
        try {
            factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
        } catch (e: XmlPullParserException) {
            throw RuntimeException("Couldn't create XmlPullParserFactory", e)
        }
    }

    @JvmStatic
    fun newPullParser() = factory.newPullParser()!!

    @JvmStatic
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
