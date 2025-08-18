/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

package at.bitfire.dav4jvm.okhttp.property.caldav

import at.bitfire.dav4jvm.okhttp.Property
import at.bitfire.dav4jvm.okhttp.PropertyFactory
import at.bitfire.dav4jvm.okhttp.XmlReader
import org.xmlpull.v1.XmlPullParser

data class CalendarData(
    val iCalendar: String?
): Property {

    companion object {
        @JvmField
        val NAME = Property.Name(NS_CALDAV, "calendar-data")

        // attributes
        const val CONTENT_TYPE = "content-type"
        const val VERSION = "version"
    }


    object Factory: PropertyFactory {

        override fun getName() = NAME

        override fun create(parser: XmlPullParser) =
            // <!ELEMENT calendar-data (#PCDATA)>
            CalendarData(XmlReader(parser).readText())

    }

}
