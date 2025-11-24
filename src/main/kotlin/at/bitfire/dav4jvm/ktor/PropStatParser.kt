/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

package at.bitfire.dav4jvm.ktor

import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.XmlUtils.propertyName
import at.bitfire.dav4jvm.property.webdav.WebDAV
import io.ktor.http.HttpStatusCode
import org.xmlpull.v1.XmlPullParser
import java.util.LinkedList

object PropStatParser {

    private val ASSUMING_OK = HttpStatusCode(200, "Assuming OK")

    fun parse(parser: XmlPullParser): PropStat {
        val depth = parser.depth

        var status: HttpStatusCode? = null
        val prop = LinkedList<Property>()

        var eventType = parser.eventType
        while (!(eventType == XmlPullParser.END_TAG && parser.depth == depth)) {
            if (eventType == XmlPullParser.START_TAG && parser.depth == depth + 1)
                when (parser.propertyName()) {
                    WebDAV.Prop ->
                        prop.addAll(Property.parse(parser))
                    WebDAV.Status ->
                        status = KtorHttpUtils.parseStatusLine(parser.nextText())
                }
            eventType = parser.next()
        }

        return PropStat(prop, status ?: ASSUMING_OK)
    }

}