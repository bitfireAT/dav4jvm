/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.dav4android.property

import at.bitfire.dav4android.Constants
import at.bitfire.dav4android.Property
import at.bitfire.dav4android.PropertyFactory
import at.bitfire.dav4android.XmlUtils
import okhttp3.internal.http.HttpDate
import org.xmlpull.v1.XmlPullParser

class GetLastModified(
        var lastModified: Long?
): Property {

    companion object {
        @JvmField
        val NAME = Property.Name(XmlUtils.NS_WEBDAV, "getlastmodified")
    }

    constructor(rawDate: String?)
            : this(null as Long?)
    {
        if (rawDate != null) {
            val date = HttpDate.parse(rawDate)
            if (date != null)
                lastModified = date.time
            else
                Constants.log.warning("Couldn't parse Last-Modified date")
        } else
            Constants.log.warning("Last-Modified without date")
    }


    class Factory: PropertyFactory {

        override fun getName() = NAME

        override fun create(parser: XmlPullParser) =
            // <!ELEMENT getlastmodified (#PCDATA) >
            GetLastModified(XmlUtils.readText(parser))

    }

}
