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
import java.time.Instant

data class GetLastModified(
    val lastModified: Instant?
): Property {

    object Factory: PropertyFactory {

        override fun getName() = WebDAV.GetLastModified

        override fun create(parser: XmlPullParser): GetLastModified {
            // <!ELEMENT getlastmodified (#PCDATA) >
            return GetLastModified(
                XmlReader(parser).readHttpDate()
            )
        }

    }

}
