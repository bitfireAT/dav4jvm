/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

package at.bitfire.dav4jvm

import at.bitfire.dav4jvm.Response.Companion.STATUS
import at.bitfire.dav4jvm.XmlUtils.propertyName
import at.bitfire.dav4jvm.property.webdav.NS_WEBDAV
import io.ktor.http.HttpStatusCode
import okhttp3.internal.http.StatusLine
import org.xmlpull.v1.XmlPullParser
import java.net.ProtocolException
import java.util.LinkedList

/**
 * Represents a WebDAV propstat XML element.
 *
 *     <!ELEMENT propstat (prop, status, error?, responsedescription?) >
 */
data class PropStat(
        val properties: List<Property>,
        val status: HttpStatusCode,
        val error: List<Error>? = null
) {

    companion object {

        @JvmField
        val NAME = Property.Name(NS_WEBDAV, "propstat")

        private val ASSUMING_OK = HttpStatusCode(200, "Assuming OK")
        private val INVALID_STATUS = HttpStatusCode( 500, "Invalid status line")

        fun parse(parser: XmlPullParser): PropStat {
            val depth = parser.depth

            var status: HttpStatusCode? = null
            val prop = LinkedList<Property>()

            var eventType = parser.eventType
            while (!(eventType == XmlPullParser.END_TAG && parser.depth == depth)) {
                if (eventType == XmlPullParser.START_TAG && parser.depth == depth + 1)
                    when (parser.propertyName()) {
                        DavResource.PROP ->
                            prop.addAll(Property.parse(parser))
                        STATUS ->
                            status = try {
                                //TODO: Replace OKHttp Status lines with something different
                                val okHttpStatusLine = StatusLine.parse(parser.nextText())
                                HttpStatusCode(okHttpStatusLine.code, okHttpStatusLine.message)
                            } catch (e: ProtocolException) {
                                // invalid status line, treat as 500 Internal Server Error
                                INVALID_STATUS
                            }
                    }
                eventType = parser.next()
            }

            return PropStat(prop, status ?: ASSUMING_OK)
        }

    }
}