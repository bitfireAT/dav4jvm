/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm.property.push

import at.bitfire.dav4jvm.HttpUtils
import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.PropertyFactory
import at.bitfire.dav4jvm.XmlUtils
import at.bitfire.dav4jvm.XmlUtils.propertyName
import org.xmlpull.v1.XmlPullParser
import java.time.Instant

/**
 * Represents a `{DAV:Push}push-subscribe` property.
 *
 * Experimental! See https://github.com/bitfireAT/webdav-push/
 */
class PushSubscribe: Property {

    companion object {

        @JvmField
        val NAME = Property.Name(NS_WEBDAV_PUSH, "push-subscribe")

        val EXPIRES = Property.Name(NS_WEBDAV_PUSH, "expires")

    }

    var expires: Instant? = null
    var subscription: Subscription? = null


    object Factory: PropertyFactory {

        override fun getName() = NAME

        override fun create(parser: XmlPullParser): PushSubscribe {
            val subscribe = PushSubscribe()

            val depth = parser.depth
            var eventType = parser.eventType
            while (!(eventType == XmlPullParser.END_TAG && parser.depth == depth)) {
                if (eventType == XmlPullParser.START_TAG && parser.depth == depth + 1)
                    when (parser.propertyName()) {
                        EXPIRES -> {
                            val expiresDate = XmlUtils.requireReadText(parser)
                            subscribe.expires = HttpUtils.parseDate(expiresDate)
                        }
                        Subscription.NAME ->
                            subscribe.subscription = Subscription.Factory.create(parser)
                    }
                eventType = parser.next()
            }

            return subscribe
        }

    }

}