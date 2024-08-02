package at.bitfire.dav4jvm

import org.xmlpull.v1.XmlPullParser
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
            logger.warning("Couldn't parse Last-Modified date")
            null
        }
    }
}
