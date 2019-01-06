/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm.property

import at.bitfire.dav4jvm.Constants
import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.PropertyFactory
import at.bitfire.dav4jvm.XmlUtils
import okhttp3.internal.http.HttpDate
import org.xmlpull.v1.XmlPullParser

data class GetLastModified(
        var lastModified: Long
): Property {

    companion object {
        @JvmField
        val NAME = Property.Name(XmlUtils.NS_WEBDAV, "getlastmodified")
    }


    class Factory: PropertyFactory {

        override fun getName() = NAME

        override fun create(parser: XmlPullParser): GetLastModified? {
            // <!ELEMENT getlastmodified (#PCDATA) >
            XmlUtils.readText(parser)?.let { rawDate ->
                val date = HttpDate.parse(rawDate)
                if (date != null)
                    return GetLastModified(date.time)
                else
                    Constants.log.warning("Couldn't parse Last-Modified date")
            }
            return null
        }

    }

}
