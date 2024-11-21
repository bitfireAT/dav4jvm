/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm.property.push

import at.bitfire.dav4jvm.PropStat
import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.PropertyFactory
import at.bitfire.dav4jvm.XmlReader
import org.xmlpull.v1.XmlPullParser

/**
 * Represents a [NS_WEBDAV_PUSH]`:push-message` property.
 *
 * Experimental! See https://github.com/bitfireAT/webdav-push/
 */
class PushMessage(
    val propStat: PropStat?
): Property {

    companion object {

        @JvmField
        val NAME = Property.Name(NS_WEBDAV_PUSH, "push-message")

    }


    object Factory: PropertyFactory {

        override fun getName() = NAME

        override fun create(parser: XmlPullParser): PushMessage {
            var propStat: PropStat? = null

            XmlReader(parser).processTag(PropStat.NAME) {
                propStat = PropStat.parse(parser)
            }

            return PushMessage(propStat)
        }

    }

}