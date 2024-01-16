/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm

import at.bitfire.dav4jvm.exception.InvalidPropertyException
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory
import org.xmlpull.v1.XmlSerializer
import java.io.IOException

object XmlUtils {

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
    fun processTag(parser: XmlPullParser, name: Property.Name, processor: () -> Unit) {
        val depth = parser.depth
        var eventType = parser.eventType
        while (!((eventType == XmlPullParser.END_TAG || eventType == XmlPullParser.END_DOCUMENT) && parser.depth == depth)) {
            if (eventType == XmlPullParser.START_TAG && parser.depth == depth + 1 && parser.propertyName() == name)
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

    /**
     * Same as [readText], but requires a [XmlPullParser.TEXT] value.
     *
     * @throws InvalidPropertyException when no text could be read
     */
    @Throws(InvalidPropertyException::class, IOException::class, XmlPullParserException::class)
    fun requireReadText(parser: XmlPullParser): String =
        readText(parser) ?:
        throw InvalidPropertyException("XML text for ${parser.namespace}:${parser.name} must not be empty")

    @Throws(IOException::class, XmlPullParserException::class)
    fun readTextProperty(parser: XmlPullParser, name: Property.Name): String? {
        val depth = parser.depth
        var eventType = parser.eventType
        var result: String? = null
        while (!((eventType == XmlPullParser.END_TAG || eventType == XmlPullParser.END_DOCUMENT) && parser.depth == depth)) {
            if (eventType == XmlPullParser.START_TAG && parser.depth == depth + 1 && parser.propertyName() == name)
                result = parser.nextText()
            eventType = parser.next()
        }
        return result
    }

    @Throws(IOException::class, XmlPullParserException::class)
    fun readTextPropertyList(parser: XmlPullParser, name: Property.Name, list: MutableCollection<String>) {
        val depth = parser.depth
        var eventType = parser.eventType
        while (!((eventType == XmlPullParser.END_TAG || eventType == XmlPullParser.END_DOCUMENT) && parser.depth == depth)) {
            if (eventType == XmlPullParser.START_TAG && parser.depth == depth + 1 && parser.propertyName() == name)
                list.add(parser.nextText())
            eventType = parser.next()
        }
    }


    fun XmlSerializer.insertTag(name: Property.Name, contentGenerator: XmlSerializer.() -> Unit = {}) {
        startTag(name.namespace, name.name)
        contentGenerator(this)
        endTag(name.namespace, name.name)
    }

    fun XmlPullParser.propertyName(): Property.Name {
        val propNs = namespace
        val propName = name
        if (propNs == null || propName == null)
            throw IllegalStateException("Current event must be START_TAG or END_TAG")
        return Property.Name(propNs, propName)
    }

}
