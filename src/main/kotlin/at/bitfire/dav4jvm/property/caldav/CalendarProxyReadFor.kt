/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

package at.bitfire.dav4jvm.property.caldav

import at.bitfire.dav4jvm.property.common.HrefListProperty
import org.xmlpull.v1.XmlPullParser

data class CalendarProxyReadFor(
    override val hrefs: List<String> = emptyList()
): HrefListProperty(hrefs) {

    object Factory: HrefListProperty.Factory() {

        override fun getName() = CalDAV.CalendarProxyReadFor

        override fun create(parser: XmlPullParser) = create(parser, ::CalendarProxyReadFor)

    }

}
