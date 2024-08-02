/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm.property.webdav

import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.PropertyFactory
import at.bitfire.dav4jvm.readInstantOrNull
import org.xmlpull.v1.XmlPullParser
import java.time.Instant

data class GetLastModified(
    var lastModified: Instant?
): Property {

    companion object {
        @JvmField
        val NAME = Property.Name(NS_WEBDAV, "getlastmodified")
    }


    object Factory: PropertyFactory {

        override fun getName() = NAME

        override fun create(parser: XmlPullParser): GetLastModified {
            // <!ELEMENT getlastmodified (#PCDATA) >
            return GetLastModified(
                readInstantOrNull(parser)
            )
        }

    }

}
