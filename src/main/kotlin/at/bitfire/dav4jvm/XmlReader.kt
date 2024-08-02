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

class XmlReader(
    private val parser: XmlPullParser
) {
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

    @Throws(IOException::class, XmlPullParserException::class)
    fun readTextProperty(name: Property.Name): String? {
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
    fun readTextPropertyList(name: Property.Name, list: MutableCollection<String>) {
        val depth = parser.depth
        var eventType = parser.eventType
        while (!((eventType == XmlPullParser.END_TAG || eventType == XmlPullParser.END_DOCUMENT) && parser.depth == depth)) {
            if (eventType == XmlPullParser.START_TAG && parser.depth == depth + 1 && parser.propertyName() == name)
                list.add(parser.nextText())
            eventType = parser.next()
        }
    }

    /**
     * Uses [readText] to read the tag's value as String, and converts it into a [Long] with [String.toLong].
     * If the conversion fails for any reason, null is returned, and a message is displayed in log.
     *
     * **Only intended to be used by [PropertyFactory.create]**. Do not use outside of [PropertyFactory].
     */
    fun readLongOrNull(): Long? {
        return readText()?.let { valueStr ->
            try {
                valueStr.toLong()
            } catch(e: NumberFormatException) {
                val logger = Logger.getLogger(javaClass.name)
                logger.log(Level.WARNING, "Couldn't parse property: $valueStr", e)
                null
            }
        }
    }

    /**
     * Uses [readText] to read the tag's value as String, and converts it into an [Instant] using [HttpUtils.parseDate].
     * If the conversion fails for any reason, null is returned, and a message is displayed in log.
     *
     * **Only intended to be used by [PropertyFactory.create]**. Do not use outside of [PropertyFactory].
     */
    fun readHttpDateOrNull(): Instant? {
        return readText()?.let { rawDate ->
            val date = HttpUtils.parseDate(rawDate)
            if (date != null)
                date
            else {
                val logger = Logger.getLogger(javaClass.name)
                logger.warning("Couldn't parse date")
                null
            }
        }
    }

    /**
     * Processes all the tags named [tagName], and sends them with [onNewType].
     *
     * **Only intended to be used by [PropertyFactory.create]**. Do not use outside of [PropertyFactory].
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
            logger.log(Level.SEVERE, "Couldn't parse <resourcetype>", e)
        }
    }

}
