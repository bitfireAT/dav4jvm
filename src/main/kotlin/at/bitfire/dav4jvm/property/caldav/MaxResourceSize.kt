/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm.property.caldav

import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.PropertyFactory
import at.bitfire.dav4jvm.readLongOrNull
import org.xmlpull.v1.XmlPullParser

data class MaxResourceSize(
    val maxSize: Long?
) : Property {
    companion object {
        @JvmField
        val NAME = Property.Name(NS_CALDAV, "max-resource-size")
    }

    object Factory: PropertyFactory<MaxResourceSize> {
        override fun getName() = NAME

        override fun create(parser: XmlPullParser): MaxResourceSize {
            return MaxResourceSize(readLongOrNull(parser))
        }
    }
}