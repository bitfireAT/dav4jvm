/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm.property.webdav

import at.bitfire.dav4jvm.Property
import org.xmlpull.v1.XmlPullParser

class GroupMembership(
    override val hrefs: List<String>
): HrefListProperty(hrefs) {

    companion object {

        @JvmField
        val NAME = Property.Name(NS_WEBDAV, "group-membership")

    }


    object Factory: HrefListProperty.Factory() {

        override fun getName() = NAME

        override fun create(parser: XmlPullParser) = create(parser, ::GroupMembership)

    }

}
