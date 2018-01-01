/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.dav4android.property

import at.bitfire.dav4android.Property
import at.bitfire.dav4android.PropertyFactory
import at.bitfire.dav4android.QuotedStringUtils
import at.bitfire.dav4android.XmlUtils
import org.xmlpull.v1.XmlPullParser

class GetETag(
        rawETag: String?
): Property {

    companion object {
        @JvmField
        val NAME = Property.Name(XmlUtils.NS_WEBDAV, "getetag")
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

            tag = QuotedStringUtils.decodeQuotedString(tag)
        }

        eTag = tag
    }

    override fun toString() = eTag ?: "(null)"


    class Factory(): PropertyFactory {

        override fun getName() = NAME

        override fun create(parser: XmlPullParser) =
                GetETag(XmlUtils.readText(parser))

    }

}
