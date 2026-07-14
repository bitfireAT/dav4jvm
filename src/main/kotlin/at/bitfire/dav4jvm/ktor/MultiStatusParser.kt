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

import at.bitfire.dav4jvm.XmlReader
import at.bitfire.dav4jvm.XmlUtils.propertyName
import at.bitfire.dav4jvm.property.webdav.SyncToken
import at.bitfire.dav4jvm.property.webdav.WebDAV
import io.ktor.http.Url
import kotlinx.coroutines.flow.FlowCollector
import org.xmlpull.v1.XmlPullParser

/**
 * Parses a WebDAV `<multistatus>` XML response.
 *
 * @param location  location of the request (used to resolve possible relative `<href>` in responses)
 */
class MultiStatusParser(
    private val location: Url
) {

    suspend fun parseResponse(parser: XmlPullParser, collector: FlowCollector<MultiStatusItem>) {
        val responseParser = ResponseParser(location)

        // <!ELEMENT multistatus (response*, responsedescription?,
        //                        sync-token?) >
        val depth = parser.depth
        var eventType = parser.eventType
        while (!(eventType == XmlPullParser.END_TAG && parser.depth == depth)) {
            if (eventType == XmlPullParser.START_TAG && parser.depth == depth + 1) {
                val item = when (parser.propertyName()) {
                    WebDAV.Response ->
                        responseParser.parseResponse(parser)
                    WebDAV.SyncToken ->
                        XmlReader(parser).readText()?.let { MultiStatusItem.ExtraProperty(SyncToken(it)) }
                    else -> null
                }
                if (item != null)
                    collector.emit(item)
            }
            eventType = parser.next()
        }
    }

}