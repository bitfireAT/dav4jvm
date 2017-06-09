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
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.util.logging.Level

// see RFC 5397: WebDAV Current Principal Extension

data class CurrentUserPrincipal(
        val href: String?
): Property {

    companion object {
        @JvmField
        val NAME = Property.Name(XmlUtils.NS_WEBDAV, "current-user-principal")
    }


    class Factory: PropertyFactory {

        override fun getName() = NAME

        override fun create(parser: XmlPullParser): CurrentUserPrincipal {
            var href: String? = null

            try {
                // <!ELEMENT current-user-principal (unauthenticated | href)>
                val depth = parser.depth

                var eventType = parser.eventType
                while (!(eventType == XmlPullParser.END_TAG && parser.depth == depth)) {
                    if (eventType == XmlPullParser.START_TAG && parser.depth == depth+1 &&
                            parser.namespace == XmlUtils.NS_WEBDAV && parser.name == "href")
                            href = parser.nextText()
                    eventType = parser.next()
                }
            } catch(e: XmlPullParserException) {
                Constants.log.log(Level.SEVERE, "Couldn't parse <current-user-principal>", e);
            }

            return CurrentUserPrincipal(href)
        }

    }

}
