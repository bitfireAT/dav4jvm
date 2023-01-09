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

/**
 * The GetETag property.
 *
 * Can also be used to parse ETags from HTTP responses – just pass the raw ETag
 * header value to the constructor and then use [eTag] and [weak].
 */
class GetETag(
    rawETag: String
): Property {

    companion object {
        @JvmField
        val NAME = Property.Name(XmlUtils.NS_WEBDAV, "getetag")

        fun fromResponse(response: Response) =
            response.header("ETag")?.let { GetETag(it) }
    }

    /**
     * The parsed ETag value, excluding the weakness indicator and the quotes.
     */
    val eTag: String

    /**
     * Whether the ETag is weak.
     */
    var weak: Boolean

    init {
        /* entity-tag = [ weak ] opaque-tag
           weak       = "W/"
           opaque-tag = quoted-string
        */
        val tag: String

        // remove trailing "W/"
        if (rawETag.startsWith("W/") && rawETag.length >= 2) {
            // entity tag is weak
            tag = rawETag.substring(2)
            weak = true
        } else {
            tag = rawETag
            weak = false
        }

        eTag = QuotedStringUtils.decodeQuotedString(tag)
    }

    override fun toString() = "ETag(weak=${weak}, tag=$eTag)"

    override fun equals(other: Any?): Boolean {
        if (other !is GetETag)
            return false
        return eTag == other.eTag && weak == other.weak
    }

    override fun hashCode(): Int {
        return eTag.hashCode() xor weak.hashCode()
    }


    object Factory: PropertyFactory {

        override fun getName() = NAME

        override fun create(parser: XmlPullParser) =
            GetETag(XmlUtils.requireReadText(parser))

    }

}
