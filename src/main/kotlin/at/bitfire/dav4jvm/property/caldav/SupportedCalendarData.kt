/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm.property.caldav

import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.PropertyFactory
import at.bitfire.dav4jvm.XmlUtils
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.util.logging.Level
import java.util.logging.Logger

class SupportedCalendarData: Property {

    companion object {

        @JvmField
        val NAME = Property.Name(NS_CALDAV, "supported-calendar-data")

        val CALENDAR_DATA_TYPE = Property.Name(NS_CALDAV, "calendar-data")
        const val CONTENT_TYPE = "content-type"
        const val VERSION = "version"

    }

    val types = mutableSetOf<MediaType>()

    fun hasJCal() = types.any { "application".equals(it.type, true) && "calendar+json".equals(it.subtype, true) }

    override fun toString() = "[${types.joinToString(", ")}]"


    object Factory: PropertyFactory {

        override fun getName() = NAME

        override fun create(parser: XmlPullParser): SupportedCalendarData? {
            val supported = SupportedCalendarData()

            try {
                XmlUtils.processTag(parser, CALENDAR_DATA_TYPE) {
                    parser.getAttributeValue(null, CONTENT_TYPE)?.let { contentType ->
                        var type = contentType
                        parser.getAttributeValue(null, VERSION)?.let { version -> type += "; version=$version" }
                        type.toMediaTypeOrNull()?.let { supported.types.add(it) }
                    }
                }
            } catch(e: XmlPullParserException) {
                val logger = Logger.getLogger(javaClass.name)
                logger.log(Level.SEVERE, "Couldn't parse <resourcetype>", e)
                return null
            }

            return supported
        }

    }

}
