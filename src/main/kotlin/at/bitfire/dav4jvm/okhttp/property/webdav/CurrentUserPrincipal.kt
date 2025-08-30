/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

package at.bitfire.dav4jvm.okhttp.property.webdav

import at.bitfire.dav4jvm.okhttp.DavResource
import at.bitfire.dav4jvm.okhttp.Property
import at.bitfire.dav4jvm.okhttp.PropertyFactory
import at.bitfire.dav4jvm.okhttp.XmlReader
import org.xmlpull.v1.XmlPullParser

// see RFC 5397: WebDAV Current Principal Extension

data class CurrentUserPrincipal(
    val href: String?
): Property {

    companion object {

        @JvmField
        val NAME = Property.Name(NS_WEBDAV, "current-user-principal")

    }


    object Factory: PropertyFactory {

        override fun getName() = NAME

        override fun create(parser: XmlPullParser): CurrentUserPrincipal {
            // <!ELEMENT current-user-principal (unauthenticated | href)>
            var href: String? = null
            XmlReader(parser).processTag(DavResource.HREF) {
                href = readText()
            }
            return CurrentUserPrincipal(href)
        }

    }

}
