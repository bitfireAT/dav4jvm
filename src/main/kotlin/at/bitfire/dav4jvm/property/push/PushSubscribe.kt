/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm.property.push

import at.bitfire.dav4jvm.Dav4jvm
import at.bitfire.dav4jvm.HttpUtils
import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.PropertyFactory
import at.bitfire.dav4jvm.XmlUtils
import at.bitfire.dav4jvm.XmlUtils.propertyName
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.time.Instant
import java.util.logging.Level

class PushSubscribe: Property {

    companion object {

        @JvmField
        val NAME = Property.Name(NS_WEBDAV_PUSH, "push-subscribe")

        val EXPIRES = Property.Name(NS_WEBDAV_PUSH, "expires")
        val WEB_PUSH_SUBSCRIPTION = Property.Name(NS_WEBDAV_PUSH, "web-push-subscription")

    }

    var expires: Instant? = null
    var webPushSubscription: WebPushSubscription? = null


    object Factory: PropertyFactory {

        override fun getName() = NAME

        override fun create(parser: XmlPullParser): PushSubscribe? {
            val subscribe = PushSubscribe()

            try {
                val depth = parser.depth
                var eventType = parser.eventType
                while (!(eventType == XmlPullParser.END_TAG && parser.depth == depth)) {
                    if (eventType == XmlPullParser.START_TAG && parser.depth == depth + 1)
                        when (parser.propertyName()) {
                            EXPIRES -> {
                                val expiresDate = XmlUtils.requireReadText(parser)
                                subscribe.expires = HttpUtils.parseDate(expiresDate)
                            }
                            WEB_PUSH_SUBSCRIPTION ->
                                subscribe.webPushSubscription = WebPushSubscription.Factory.create(parser)
                        }
                    eventType = parser.next()
                }
            } catch (e: XmlPullParserException) {
                Dav4jvm.log.log(Level.SEVERE, "Couldn't parse <push-subscribe>", e)
                return null
            }

            return subscribe
        }

    }

}