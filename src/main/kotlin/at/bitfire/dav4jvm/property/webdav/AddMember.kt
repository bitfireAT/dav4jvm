/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm.property.webdav

import at.bitfire.dav4jvm.DavResource
import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.PropertyFactory
import at.bitfire.dav4jvm.XmlUtils
import org.xmlpull.v1.XmlPullParser

/**
 * Defined in RFC 5995 3.2.1 DAV:add-member Property (Protected).
 */
data class AddMember(
        val href: String?
): Property {
    companion object {
        @JvmField
        val NAME = Property.Name(NS_WEBDAV, "add-member")
    }

    object Factory: PropertyFactory {
        override fun getName() = NAME

        override fun create(parser: XmlPullParser) =
                AddMember(XmlUtils.readTextProperty(parser, DavResource.HREF))
    }
}