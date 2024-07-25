/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm.property.push

import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.PropertyFactory
import at.bitfire.dav4jvm.XmlUtils
import at.bitfire.dav4jvm.exception.InvalidPropertyException
import org.xmlpull.v1.XmlPullParser
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Represents a `{DAV:Push}topic` property.
 *
 * Experimental! See https://github.com/bitfireAT/webdav-push/
 */
class Topic private constructor(
    val topic: String
): Property {

    companion object {

        @JvmField
        val NAME = Property.Name(NS_WEBDAV_PUSH, "topic")

    }


    object Factory: PropertyFactory {

        private val logger = Logger.getLogger(javaClass.name)

        override fun getName() = NAME

        override fun create(parser: XmlPullParser): Topic? = try {
            Topic(XmlUtils.requireReadText(parser))
        } catch (e: InvalidPropertyException) {
            logger.log(Level.INFO, "Invalid or missing topic property. Push is not supported", e)
            null
        }

    }

}