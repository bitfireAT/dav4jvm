/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

package at.bitfire.dav4jvm.ktor

import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.XmlUtils
import at.bitfire.dav4jvm.property.webdav.DisplayName
import at.bitfire.dav4jvm.property.webdav.GetETag
import org.junit.Assert.assertEquals
import org.junit.Test
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader

class PropertyTest {

    companion object {
        private fun parseProperty(xml: String): List<Property> {
            val parser = XmlUtils.newPullParser()
            parser.setInput(StringReader("<test>$xml</test>"))
            parser.nextTag()    // move into <test>
            return Property.parse(parser)
        }
    }


    @Test
    fun testParse_emptyElement() {
        val parser = XmlUtils.newPullParser()
        parser.setInput(StringReader("<test xmlns='DAV:'></test>"))
        parser.nextTag()
        val result = Property.parse(parser)
        assertEquals(0, result.size)
        assertEquals(XmlPullParser.END_TAG, parser.eventType)
        assertEquals("test", parser.name)
    }

    @Test
    fun testParse_missingEtagValue() {
        val result = parseProperty("<getetag xmlns='DAV:'/>")
        assertEquals(1, result.size)
        assertEquals(GetETag(null), result[0])
    }

    @Test
    fun testParse_etagWithValue() {
        val result = parseProperty("<getetag xmlns='DAV:'>12345</getetag>")
        assertEquals(GetETag("12345"), result.first())
    }

    @Test
    fun testParse_unknownProperty() {
        val result = parseProperty("<unknown xmlns='http://ns.example.com/'/>")
        assertEquals(0, result.size)
    }

    @Test
    fun testParse_multipleProperties() {
        val result = parseProperty(
            "<getetag xmlns='DAV:'>\"v1\"</getetag>" +
                    "<displayname xmlns='DAV:'>My Calendar</displayname>"
        )
        assertEquals(2, result.size)
        assertEquals(GetETag("\"v1\""), result.filterIsInstance<GetETag>().first())
        assertEquals(DisplayName("My Calendar"), result.filterIsInstance<DisplayName>().first())
    }

    @Test
    fun testParse_knownAndUnknownMixed() {
        val result = parseProperty(
            "<getetag xmlns='DAV:'>\"abc\"</getetag>" +
                    "<unknown xmlns='http://ns.example.com/'/>" +
                    "<displayname xmlns='DAV:'>X</displayname>"
        )
        assertEquals(2, result.size)
        assertEquals(GetETag("\"abc\""), result.filterIsInstance<GetETag>().first())
        assertEquals(DisplayName("X"), result.filterIsInstance<DisplayName>().first())
    }

}
