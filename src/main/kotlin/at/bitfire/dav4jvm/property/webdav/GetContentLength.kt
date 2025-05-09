/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

package at.bitfire.dav4jvm.property.webdav

import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.PropertyFactory
import at.bitfire.dav4jvm.XmlReader
import org.xmlpull.v1.XmlPullParser

data class GetContentLength(
    val contentLength: Long?
) : Property {

    companion object {

        @JvmField
        val NAME = Property.Name(NS_WEBDAV, "getcontentlength")

    }

    object Factory: PropertyFactory {

        override fun getName() = NAME

        override fun create(parser: XmlPullParser) =
            GetContentLength(XmlReader(parser).readLong())

    }

}
