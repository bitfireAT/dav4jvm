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
import java.net.URI
import java.net.URISyntaxException

/**
 * Represents a [NS_WEBDAV_PUSH]`:push-resource` property.
 *
 * Experimental! See https://github.com/bitfireAT/webdav-push/
 */
data class PushResource(
    val uri: URI? = null
): Property {

    object Factory: PropertyFactory {

        override fun getName() = WebDAVPush.PushResource

        override fun create(parser: XmlPullParser): PushResource =
            PushResource(
                uri = XmlReader(parser).readText()?.let { uri ->
                    try {
                        URI(uri)
                    } catch (_: URISyntaxException) {
                        null
                    }
                }
            )

    }

}