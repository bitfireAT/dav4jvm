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
import at.bitfire.dav4jvm.XmlReader
import at.bitfire.dav4jvm.XmlUtils.propertyName
import at.bitfire.dav4jvm.property.webdav.SyncToken
import at.bitfire.dav4jvm.property.webdav.WebDAV
import io.ktor.http.Url
import org.xmlpull.v1.XmlPullParser

/**
 * Parses a WebDAV `<multistatus>` XML response.
 *
 * @param location  location of the request (used to resolve possible relative `<href>` in responses)
 */
class MultiStatusParser(
    private val location: Url,
    private val callback: MultiResponseCallback
) {

    suspend fun parseResponse(parser: XmlPullParser): List<Property> {
        val responseProperties = mutableListOf<Property>()
        val responseParser = ResponseParser(location, callback)

        // <!ELEMENT multistatus (response*, responsedescription?,
        //                        sync-token?) >
        val depth = parser.depth
        var eventType = parser.eventType
        while (!(eventType == XmlPullParser.END_TAG && parser.depth == depth)) {
            if (eventType == XmlPullParser.START_TAG && parser.depth == depth + 1)
                when (parser.propertyName()) {
                    WebDAV.Response ->
                        responseParser.parseResponse(parser)
                    WebDAV.SyncToken ->
                        XmlReader(parser).readText()?.let {
                            responseProperties += SyncToken(it)
                        }
                }
            eventType = parser.next()
        }

        return responseProperties
    }

}