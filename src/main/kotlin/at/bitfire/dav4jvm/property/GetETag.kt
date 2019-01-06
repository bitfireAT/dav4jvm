/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm.property

import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.PropertyFactory
import at.bitfire.dav4jvm.QuotedStringUtils
import at.bitfire.dav4jvm.XmlUtils
import okhttp3.Response
import org.xmlpull.v1.XmlPullParser

class GetETag(
        rawETag: String?
): Property {

    companion object {
        @JvmField
        val NAME = Property.Name(XmlUtils.NS_WEBDAV, "getetag")

        fun fromResponse(response: Response) =
                response.header("ETag")?.let { GetETag(it) }
    }

    var eTag: String?

    init {
        /* entity-tag = [ weak ] opaque-tag
           weak       = "W/"
           opaque-tag = quoted-string
        */
        var tag: String? = rawETag
        tag?.let {
            // remove trailing "W/"
            if (it.startsWith("W/") && it.length >= 3)
            // entity tag is weak (doesn't matter for us)
                tag = it.substring(2)

            tag?.let { tag = QuotedStringUtils.decodeQuotedString(it) }
        }

        eTag = tag
    }

    override fun toString() = eTag ?: "(null)"


    class Factory : PropertyFactory {

        override fun getName() = NAME

        override fun create(parser: XmlPullParser) =
                GetETag(XmlUtils.readText(parser))

    }

}
