/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

package at.bitfire.dav4jvm.ktor.property.push

import at.bitfire.dav4jvm.ktor.Property
import at.bitfire.dav4jvm.ktor.PropertyFactory
import at.bitfire.dav4jvm.ktor.XmlUtils.propertyName
import at.bitfire.dav4jvm.ktor.property.webdav.Depth
import at.bitfire.dav4jvm.ktor.property.webdav.SyncLevel
import at.bitfire.dav4jvm.ktor.property.webdav.SyncToken
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

    companion object {

        @JvmField
        val NAME = Property.Name(NS_WEBDAV_PUSH, "content-update")

    }


    object Factory: PropertyFactory {

        override fun getName() = NAME

        override fun create(parser: XmlPullParser): ContentUpdate {
            var contentUpdate = ContentUpdate()

            val depth = parser.depth
            var eventType = parser.eventType
            while (!(eventType == XmlPullParser.END_TAG && parser.depth == depth)) {
                if (eventType == XmlPullParser.START_TAG && parser.depth == depth + 1) {
                    when (parser.propertyName()) {
                        SyncLevel.NAME -> contentUpdate = contentUpdate.copy(
                            depth = Depth.Factory.create(parser)
                        )
                        SyncToken.NAME -> contentUpdate = contentUpdate.copy(
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