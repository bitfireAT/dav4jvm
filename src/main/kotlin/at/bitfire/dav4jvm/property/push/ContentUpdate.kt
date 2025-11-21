/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

package at.bitfire.dav4jvm.property.push

import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.PropertyFactory
import at.bitfire.dav4jvm.XmlUtils.propertyName
import at.bitfire.dav4jvm.property.webdav.Depth
import at.bitfire.dav4jvm.property.webdav.SyncToken
import at.bitfire.dav4jvm.property.webdav.WebDAV
import org.xmlpull.v1.XmlPullParser

/**
 * Represents a [NS_WEBDAV_PUSH]`:content-update` property.
 *
 * Experimental! See https://github.com/bitfireAT/webdav-push/
 */
data class ContentUpdate(
    val depth: Depth? = null,
    val syncToken: SyncToken? = null
): Property {

    object Factory: PropertyFactory {

        override fun getName() = WebDAVPush.ContentUpdate

        override fun create(parser: XmlPullParser): ContentUpdate {
            var contentUpdate = ContentUpdate()

            val depth = parser.depth
            var eventType = parser.eventType
            while (!(eventType == XmlPullParser.END_TAG && parser.depth == depth)) {
                if (eventType == XmlPullParser.START_TAG && parser.depth == depth + 1) {
                    when (parser.propertyName()) {
                        WebDAV.SyncLevel -> contentUpdate = contentUpdate.copy(
                            depth = Depth.Factory.create(parser)
                        )
                        WebDAV.SyncToken -> contentUpdate = contentUpdate.copy(
                            syncToken = SyncToken.Factory.create(parser)
                        )
                    }
                }
                eventType = parser.next()
            }

            return contentUpdate
        }

    }

}