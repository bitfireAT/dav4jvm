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
import nl.adaptivity.xmlutil.QName
import nl.adaptivity.xmlutil.XmlReader
import kotlin.jvm.JvmField

data class CalendarColor(
    val color: Int
) : Property {

    companion object {
        @JvmField
        val NAME = QName(XmlUtils.NS_APPLE_ICAL, "calendar-color")

        private val PATTERN = "#?(\\p{XDigit}{6})(\\p{XDigit}{2})?".toRegex()

        /**
         * Converts a WebDAV color from one of these formats:
         *   #RRGGBB     (alpha = 0xFF)
         *   RRGGBB      (alpha = 0xFF)
         *   #RRGGBBAA
         *   RRGGBBAA
         *  to an [Int] with alpha.
         */
        @Throws(IllegalArgumentException::class)
        fun parseARGBColor(davColor: String): Int {
            val m = PATTERN.find(davColor)
            if (m != null) {
                val color_rgb = m.groupValues[1].toInt(16)
                val color_alpha = m.groupValues.elementAtOrNull(2)?.let { it.toInt( 16) and 0xFF } ?: 0xFF
                return (color_alpha shl 24) or color_rgb
            } else
                throw IllegalArgumentException("Couldn't parse color value: $davColor")
        }
    }


    object Factory : PropertyFactory {

        override fun getName() = NAME

        override fun create(parser: XmlReader): CalendarColor? {
            XmlUtils.readText(parser)?.let {
                try {
                    return CalendarColor(parseARGBColor(it))
                } catch (e: IllegalArgumentException) {
                    Dav4jvm.log.warn("Couldn't parse color, ignoring", e)
                }
            }
            return null
        }

    }

}
