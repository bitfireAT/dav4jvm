/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm.property.push

import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.PropertyFactory
import at.bitfire.dav4jvm.XmlUtils.propertyName
import org.xmlpull.v1.XmlPullParser

/**
 * Represents a [NS_WEBDAV_PUSH]`:web-push-subscription` property.
 *
 * Experimental! See https://github.com/bitfireAT/webdav-push/
 */
data class WebPushSubscription(
    val pushResource: PushResource? = null,
    val subscriptionPublicKey: SubscriptionPublicKey? = null,
    val authSecret: AuthSecret? = null
): Property {

    companion object {

        @JvmField
        val NAME = Property.Name(NS_WEBDAV_PUSH, "web-push-subscription")

    }


    object Factory: PropertyFactory {

        override fun getName() = NAME

        override fun create(parser: XmlPullParser): WebPushSubscription {
            var subscription = WebPushSubscription()

            val depth = parser.depth
            var eventType = parser.eventType
            while (!(eventType == XmlPullParser.END_TAG && parser.depth == depth)) {
                if (eventType == XmlPullParser.START_TAG && parser.depth == depth + 1) {
                    when (parser.propertyName()) {
                        PushResource.NAME -> subscription = subscription.copy(pushResource = PushResource.Factory.create(parser))
                        SubscriptionPublicKey.NAME -> subscription = subscription.copy(subscriptionPublicKey = SubscriptionPublicKey.Factory.create(parser))
                        AuthSecret.NAME -> subscription = subscription.copy(authSecret = AuthSecret.Factory.create(parser))
                    }
                }
                eventType = parser.next()
            }

            return subscription
        }

    }

}
