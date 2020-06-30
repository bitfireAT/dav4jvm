/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm.property

import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.PropertyFactory
import at.bitfire.dav4jvm.QuotedStringUtils
import at.bitfire.dav4jvm.XmlUtils
import okhttp3.Response
import org.xmlpull.v1.XmlPullParser

class ScheduleTag(
        rawScheduleTag: String?
): Property {

    companion object {
        @JvmField
        val NAME = Property.Name(XmlUtils.NS_CALDAV, "schedule-tag")

        fun fromResponse(response: Response) =
                response.header("Schedule-Tag")?.let { ScheduleTag(it) }
    }

    /* Value:  opaque-tag
       opaque-tag = quoted-string
    */
    val scheduleTag: String? = rawScheduleTag?.let { QuotedStringUtils.decodeQuotedString(it) }

    override fun toString() = scheduleTag ?: "(null)"


    class Factory : PropertyFactory {

        override fun getName() = NAME

        override fun create(parser: XmlPullParser) =
                ScheduleTag(XmlUtils.readText(parser))

    }

}
