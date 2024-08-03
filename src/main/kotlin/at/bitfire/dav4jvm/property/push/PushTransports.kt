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
 * Represents a `{DAV:Push}push-transports` property.
 *
 * Experimental! See https://github.com/bitfireAT/webdav-push/
 */
class PushTransports private constructor(
    val transports: Set<Property.Name>
): Property {

    companion object {
        @JvmField
        val NAME = Property.Name(NS_WEBDAV_PUSH, "push-transports")

        val TRANSPORT = Property.Name(NS_WEBDAV_PUSH, "transport")
        val WEB_PUSH = Property.Name(NS_WEBDAV_PUSH, "web-push")
    }

    fun hasWebPush() = transports.contains(WEB_PUSH)


    object Factory: PropertyFactory {

        override fun getName() = NAME

        override fun create(parser: XmlPullParser): PushTransports {
            val transports = mutableListOf<Property.Name>()
            XmlReader(parser).processTag(TRANSPORT) {
                val depth = parser.depth
                var eventType = parser.eventType
                while (!(eventType == XmlPullParser.END_TAG && parser.depth == depth)) {
                    if (eventType == XmlPullParser.START_TAG && parser.depth == depth + 1)
                        transports += parser.propertyName()
                    eventType = parser.next()
                }
            }
            return PushTransports(transports.toSet())
        }

    }

}