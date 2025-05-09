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
import org.xmlpull.v1.XmlPullParser

/**
 * Represents a [NS_WEBDAV_PUSH]`:web-push` property.
 *
 * Experimental! See https://github.com/bitfireAT/webdav-push/
 */
data class WebPush(
    val vapidPublicKey: VapidPublicKey? = null
) : PushTransport {

    companion object {
        @JvmField
        val NAME = Property.Name(NS_WEBDAV_PUSH, "web-push")
    }


    object Factory : PropertyFactory {

        override fun getName(): Property.Name = NAME

        override fun create(parser: XmlPullParser): WebPush {
            var vapidPublicKey: VapidPublicKey? = null
            val depth = parser.depth
            var eventType = parser.eventType
            while (!(eventType == XmlPullParser.END_TAG && parser.depth == depth)) {
                if (eventType == XmlPullParser.START_TAG && parser.namespace == NS_WEBDAV_PUSH) {
                    when (parser.name) {
                        VapidPublicKey.NAME.name -> vapidPublicKey = VapidPublicKey.Factory.create(parser)
                    }
                }
                eventType = parser.next()
            }
            return WebPush(vapidPublicKey)
        }

    }

}