/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm.property.push

import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.PropertyFactory
import at.bitfire.dav4jvm.XmlUtils
import org.xmlpull.v1.XmlPullParser

/**
 * Represents a `{DAV:Push}push-message` property.
 *
 * Experimental! See https://github.com/bitfireAT/webdav-push/
 */
class PushMessage(
    val topic: String?
): Property {

    companion object {

        @JvmField
        val NAME = Property.Name(NS_WEBDAV_PUSH, "push-message")

    }


    object Factory: PropertyFactory {

        override fun getName() = NAME

        override fun create(parser: XmlPullParser) =
            PushMessage(XmlUtils.readTextProperty(parser, Topic.NAME))

    }

}