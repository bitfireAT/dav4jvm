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

data class Trigger(
    val contentUpdate: ContentUpdate? = null,
    val propertyUpdate: PropertyUpdate? = null
) : Property {

    object Factory : PropertyFactory {

        override fun getName() = WebDAVPush.Trigger

        override fun create(parser: XmlPullParser): Trigger {
            var trigger = Trigger()

            val depth = parser.depth
            var eventType = parser.eventType
            while (!(eventType == XmlPullParser.END_TAG && parser.depth == depth)) {
                if (eventType == XmlPullParser.START_TAG && parser.depth == depth + 1) {
                    when (parser.propertyName()) {
                        WebDAVPush.ContentUpdate -> trigger = trigger.copy(
                            contentUpdate = ContentUpdate.Factory.create(parser)
                        )
                        WebDAVPush.PropertyUpdate -> trigger = trigger.copy(
                            propertyUpdate = PropertyUpdate.Factory.create(parser)
                        )
                    }
                }
                eventType = parser.next()
            }

            return trigger
        }

    }

}
