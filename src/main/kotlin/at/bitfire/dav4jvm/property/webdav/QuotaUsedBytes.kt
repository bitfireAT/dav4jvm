/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm.property.webdav

import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.PropertyFactory
import at.bitfire.dav4jvm.XmlUtils
import org.xmlpull.v1.XmlPullParser
import java.util.logging.Level
import java.util.logging.Logger

data class QuotaUsedBytes(
        val quotaUsedBytes: Long
) : Property {
    companion object {
        @JvmField
        val NAME = Property.Name(NS_WEBDAV, "quota-used-bytes")
    }

    object Factory: PropertyFactory {
        override fun getName() = NAME

        override fun create(parser: XmlPullParser): QuotaUsedBytes? {
            XmlUtils.readText(parser)?.let { valueStr ->
                try {
                    return QuotaUsedBytes(valueStr.toLong())
                } catch(e: NumberFormatException) {
                    val logger = Logger.getLogger(javaClass.name)
                    logger.log(Level.WARNING, "Couldn't parse $NAME: $valueStr", e)
                }
            }
            return null
        }
    }
}