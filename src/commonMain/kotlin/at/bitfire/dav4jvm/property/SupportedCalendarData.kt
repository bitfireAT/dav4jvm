/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm.property

import at.bitfire.dav4jvm.Dav4jvm
import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.PropertyFactory
import at.bitfire.dav4jvm.XmlUtils
import io.ktor.http.*
import nl.adaptivity.xmlutil.QName
import nl.adaptivity.xmlutil.XmlException
import nl.adaptivity.xmlutil.XmlReader
import kotlin.jvm.JvmField

class SupportedCalendarData : Property {

    companion object {

        @JvmField
        val NAME = QName(XmlUtils.NS_CALDAV, "supported-calendar-data")

        val CALENDAR_DATA_TYPE = QName(XmlUtils.NS_CALDAV, "calendar-data")
        const val CONTENT_TYPE = "content-type"
        const val VERSION = "version"

        val jCalType = ContentType("application", "calendar+json")
    }

    val types = mutableSetOf<ContentType>()

    fun hasJCal() = types.any { jCalType == it }

    override fun toString() = "[${types.joinToString(", ")}]"


    object Factory : PropertyFactory {

        override fun getName() = NAME

        override fun create(parser: XmlReader): SupportedCalendarData? {
            val supported = SupportedCalendarData()

            try {
                XmlUtils.processTag(parser, CALENDAR_DATA_TYPE) {
                    parser.getAttributeValue(null, CONTENT_TYPE)?.let { contentType ->
                        var type = contentType.run(ContentType::parse)
                        type = parser.getAttributeValue(null, SupportedAddressData.VERSION)
                            ?.let { version -> type.withParameter("version", version) } ?: type
                        supported.types += type
                    }
                }
            } catch (e: XmlException) {
                Dav4jvm.log.error("Couldn't parse <resourcetype>", e)
                return null
            }

            return supported
        }

    }

}
