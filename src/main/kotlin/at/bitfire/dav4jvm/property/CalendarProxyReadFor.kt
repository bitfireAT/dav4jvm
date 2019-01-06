/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm.property

import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.XmlUtils
import org.xmlpull.v1.XmlPullParser

class CalendarProxyReadFor: HrefListProperty() {

    companion object {
        @JvmField
        val NAME = Property.Name(XmlUtils.NS_CALENDARSERVER, "calendar-proxy-read-for")
    }


    class Factory : HrefListProperty.Factory() {

        override fun getName() = NAME

        override fun create(parser: XmlPullParser) =
                create(parser, CalendarProxyReadFor())

    }

}
