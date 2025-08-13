/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

package at.bitfire.dav4jvm.property.caldav

import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.PropertyFactory
import at.bitfire.dav4jvm.QuotedStringUtils
import at.bitfire.dav4jvm.XmlReader
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import org.xmlpull.v1.XmlPullParser

data class ScheduleTag(
    val rawScheduleTag: String?
): Property {

    companion object {

        @JvmField
        val NAME = Property.Name(NS_CALDAV, "schedule-tag")

        fun fromResponse(response: HttpResponse) =
                response.headers[HttpHeaders.ScheduleTag]?.let { ScheduleTag(it) }

    }

    /* Value:  opaque-tag
       opaque-tag = quoted-string
    */
    val scheduleTag: String? = rawScheduleTag?.let { QuotedStringUtils.decodeQuotedString(it) }

    override fun toString() = scheduleTag ?: "(null)"


    object Factory: PropertyFactory {

        override fun getName() = NAME

        override fun create(parser: XmlPullParser) = ScheduleTag(XmlReader(parser).readText())

    }

}
