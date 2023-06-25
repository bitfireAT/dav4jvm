/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm.property

import at.bitfire.dav4jvm.XmlUtils
import nl.adaptivity.xmlutil.QName
import nl.adaptivity.xmlutil.XmlReader

class Owner : HrefListProperty() {

    companion object {
        @JvmField
        val NAME = QName(XmlUtils.NS_WEBDAV, "owner")
    }

    object Factory : HrefListProperty.Factory() {

        override fun getName() = NAME

        override fun create(parser: XmlReader) =
            create(parser, Owner())
    }
}
