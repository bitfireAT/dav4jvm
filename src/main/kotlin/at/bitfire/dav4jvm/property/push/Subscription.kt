/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm.property.push

import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.PropertyFactory
import at.bitfire.dav4jvm.XmlReader
import org.xmlpull.v1.XmlPullParser

/**
 * Represents a `{DAV:Push}subscription` property.
 *
 * Experimental! See https://github.com/bitfireAT/webdav-push/
 */
class Subscription private constructor(
    val webPushSubscription: WebPushSubscription?
): Property {

    companion object {

        @JvmField
        val NAME = Property.Name(NS_WEBDAV_PUSH, "subscription")

    }


    object Factory: PropertyFactory {

        override fun getName() = NAME

        override fun create(parser: XmlPullParser): Subscription {
            // currently we only support WebPushSubscription
            var webPushSubscription: WebPushSubscription? = null

            XmlReader(parser).processTag(WebPushSubscription.NAME) {
                webPushSubscription = WebPushSubscription.Factory.create(parser)
            }

            return Subscription(webPushSubscription)
        }

    }

}