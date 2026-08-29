/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

package at.bitfire.dav4jvm.property.push

import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.PropertyFactory
import at.bitfire.dav4jvm.XmlReader
import org.xmlpull.v1.XmlPullParser

/**
 * Represents a `content-encoding` property.
 *
 * Experimental! See https://github.com/bitfireAT/webdav-push/
 */
data class ContentEncoding(
    val encoding: String = AES128GCM
) : Property {

    companion object {
        const val AES128GCM = "aes128gcm"
    }

    object Factory : PropertyFactory {

        override fun getName() = WebDAVPush.ContentEncoding

        override fun create(parser: XmlPullParser): ContentEncoding =
            ContentEncoding(XmlReader(parser).readText() ?: AES128GCM)

    }

}
