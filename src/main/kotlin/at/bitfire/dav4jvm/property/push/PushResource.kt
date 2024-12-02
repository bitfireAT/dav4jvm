/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm.property.push

import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.PropertyFactory
import at.bitfire.dav4jvm.XmlReader
import java.net.URI
import java.net.URISyntaxException
import org.xmlpull.v1.XmlPullParser

/**
 * Represents a [NS_WEBDAV_PUSH]`:push-resource` property.
 *
 * Experimental! See https://github.com/bitfireAT/webdav-push/
 */
data class PushResource(
    val uri: URI? = null
): Property {

    companion object {

        @JvmField
        val NAME = Property.Name(NS_WEBDAV_PUSH, "push-resource")

    }


    object Factory: PropertyFactory {

        override fun getName() = NAME

        override fun create(parser: XmlPullParser): PushResource =
            PushResource(
                uri = XmlReader(parser).readText()?.let { uri ->
                    try {
                        URI(uri)
                    } catch (e: URISyntaxException) {
                        null
                    }
                }
            )

    }

}