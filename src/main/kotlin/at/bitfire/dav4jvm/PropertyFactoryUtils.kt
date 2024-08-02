package at.bitfire.dav4jvm

import at.bitfire.dav4jvm.property.caldav.SupportedCalendarData.Companion.CONTENT_TYPE
import at.bitfire.dav4jvm.property.caldav.SupportedCalendarData.Companion.VERSION
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.time.Instant
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Uses [XmlUtils.readText] to read the tag's value as String, and converts it into a [Long] with [String.toLong].
 * If the conversion fails for any reason, null is returned, and a message is displayed in log.
 *
 * **Only intended to be used by [PropertyFactory.create]**. Do not use outside of [PropertyFactory].
 *
 * @param parser The parser instance passed to [PropertyFactory.create].
 */
fun PropertyFactory.readLongOrNull(parser: XmlPullParser): Long? {
    return XmlUtils.readText(parser)?.let { valueStr ->
        try {
            valueStr.toLong()
        } catch(e: NumberFormatException) {
            val logger = Logger.getLogger(javaClass.name)
            logger.log(Level.WARNING, "Couldn't parse ${getName()}: $valueStr", e)
            null
        }
    }
}

/**
 * Uses [XmlUtils.readText] to read the tag's value as String, and converts it into an [Instant] using
 * [HttpUtils.parseDate].
 * If the conversion fails for any reason, null is returned, and a message is displayed in log.
 *
 * **Only intended to be used by [PropertyFactory.create]**. Do not use outside of [PropertyFactory].
 *
 * @param parser The parser instance passed to [PropertyFactory.create].
 */
fun PropertyFactory.readHttpDateOrNull(parser: XmlPullParser): Instant? {
    return XmlUtils.readText(parser)?.let { rawDate ->
        val date = HttpUtils.parseDate(rawDate)
        if (date != null)
            date
        else {
            val logger = Logger.getLogger(javaClass.name)
            logger.warning("Couldn't parse ${getName()} date")
            null
        }
    }
}

/**
 * Processes all the tags named [tagName], and sends them with [onNewType] on the given [parser].
 *
 * **Only intended to be used by [PropertyFactory.create]**. Do not use outside of [PropertyFactory].
 *
 * @param parser The parser instance passed to [PropertyFactory.create].
 * @param tagName The name of the tag that contains the [CONTENT_TYPE] attribute value.
 * @param onNewType Called every time a new [MediaType] is found.
 */
fun PropertyFactory.readContentTypes(parser: XmlPullParser, tagName: Property.Name, onNewType: (MediaType) -> Unit) {
    try {
        XmlUtils.processTag(parser, tagName) {
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
