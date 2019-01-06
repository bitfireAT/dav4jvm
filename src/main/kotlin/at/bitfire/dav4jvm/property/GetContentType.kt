/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm.property

import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.PropertyFactory
import at.bitfire.dav4jvm.XmlUtils
import okhttp3.MediaType
import org.xmlpull.v1.XmlPullParser

data class GetContentType(
        val type: String?
): Property {

    companion object {
        @JvmField
        val NAME = Property.Name(XmlUtils.NS_WEBDAV, "getcontenttype")
    }

    constructor(mediaType: MediaType): this(mediaType.toString())


    class Factory : PropertyFactory {

        override fun getName() = NAME

        override fun create(parser: XmlPullParser) =
                // <!ELEMENT getcontenttype (#PCDATA) >
                GetContentType(XmlUtils.readText(parser))

    }
}
