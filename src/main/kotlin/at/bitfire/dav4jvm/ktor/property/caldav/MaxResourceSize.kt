/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

package at.bitfire.dav4jvm.ktor.property.caldav

import at.bitfire.dav4jvm.ktor.Property
import at.bitfire.dav4jvm.ktor.PropertyFactory
import at.bitfire.dav4jvm.ktor.XmlReader
import org.xmlpull.v1.XmlPullParser

data class MaxResourceSize(
    val maxSize: Long?
) : Property {
    companion object {
        @JvmField
        val NAME = Property.Name(NS_CALDAV, "max-resource-size")
    }

    object Factory: PropertyFactory {
        override fun getName() = NAME

        override fun create(parser: XmlPullParser) =
            MaxResourceSize(XmlReader(parser).readLong())
    }
}