/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm.property.push

import at.bitfire.dav4jvm.Dav4jvm
import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.PropertyFactory
import at.bitfire.dav4jvm.XmlUtils
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.util.logging.Level

class WebPushSubscription: Property {

    companion object {

        @JvmField
        val NAME = Property.Name(NS_WEBDAV_PUSH, "web-push-subscription")

        val PUSH_RESOURCE = Property.Name(NS_WEBDAV_PUSH, "push-resource")

    }

    var pushResource: String? = null


    object Factory: PropertyFactory {

        override fun getName() = NAME

        override fun create(parser: XmlPullParser): WebPushSubscription? {
            val subscription = WebPushSubscription()

            try {
                subscription.pushResource = XmlUtils.readTextProperty(parser, PUSH_RESOURCE)
            } catch (e: XmlPullParserException) {
                Dav4jvm.log.log(Level.SEVERE, "Couldn't parse <web-push-subscription>", e)
                return null
            }

            return subscription
        }

    }

}