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
import org.xmlpull.v1.XmlPullParser

/**
 * Represents a [NS_WEBDAV_PUSH]`:push-message` property.
 *
 * Experimental! See https://github.com/bitfireAT/webdav-push/
 */
data class PushMessage(
    val topic: Topic? = null,
    val contentUpdate: ContentUpdate? = null,
    val propertyUpdate: PropertyUpdate? = null
): Property {

    object Factory: PropertyFactory {

        override fun getName() = WebDAVPush.PushMessage

        override fun create(parser: XmlPullParser): PushMessage {
            var message = PushMessage()

            val depth = parser.depth
            var eventType = parser.eventType
            while (!(eventType == XmlPullParser.END_TAG && parser.depth == depth)) {
                if (eventType == XmlPullParser.START_TAG && parser.depth == depth + 1) {
                    when (parser.propertyName()) {
                        WebDAVPush.Topic -> message = message.copy(
                            topic = Topic.Factory.create(parser)
                        )
                        WebDAVPush.ContentUpdate -> message = message.copy(
                            contentUpdate = ContentUpdate.Factory.create(parser)
                        )
                        WebDAVPush.PropertyUpdate -> message = message.copy(
                            propertyUpdate = PropertyUpdate.Factory.create(parser)
                        )
                    }
                }
                eventType = parser.next()
            }

            return message
        }

    }

}