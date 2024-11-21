/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm.property.push

import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.PropertyFactory
import at.bitfire.dav4jvm.XmlReader
import at.bitfire.dav4jvm.XmlUtils.propertyName
import org.xmlpull.v1.XmlPullParser

/**
 * Represents a [NS_WEBDAV_PUSH]`:web-push-subscription` property.
 *
 * Experimental! See https://github.com/bitfireAT/webdav-push/
 */
class WebPushSubscription: Property {

    companion object {

        @JvmField
        val NAME = Property.Name(NS_WEBDAV_PUSH, "web-push-subscription")

    }

    var pushResource: PushResource? = null
    var clientPublicKey: ClientPublicKey? = null
    var authSecret: AuthSecret? = null


    object Factory: PropertyFactory {

        override fun getName() = NAME

        override fun create(parser: XmlPullParser): WebPushSubscription {
            val subscription = WebPushSubscription()

            val depth = parser.depth
            var eventType = parser.eventType
            while (!(eventType == XmlPullParser.END_TAG && parser.depth == depth)) {
                if (eventType == XmlPullParser.START_TAG && parser.depth == depth + 1) {
                    when (parser.propertyName()) {
                        PushResource.NAME -> subscription.pushResource = PushResource.Factory.create(parser)
                        ClientPublicKey.NAME -> subscription.clientPublicKey = ClientPublicKey.Factory.create(parser)
                        AuthSecret.NAME -> subscription.authSecret = AuthSecret.Factory.create(parser)
                    }
                }
                eventType = parser.next()
            }

            return subscription
        }

    }

}
