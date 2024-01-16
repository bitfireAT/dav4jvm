/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm

import at.bitfire.dav4jvm.property.webdav.GetETag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

class PropertyTest {

    @Test
    fun testParse_InvalidProperty() {
        val parser = XmlPullParserFactory.newInstance().apply {
            isNamespaceAware = true
        }.newPullParser()
        parser.setInput(StringReader("<multistatus xmlns='DAV:'><getetag/></multistatus>"))
        do {
            parser.next()
        } while (parser.eventType != XmlPullParser.START_TAG && parser.name != "multistatus")

        // we're now at the start of <multistatus>
        assertEquals(XmlPullParser.START_TAG, parser.eventType)
        assertEquals("multistatus", parser.name)

        // parse invalid DAV:getetag
        assertTrue(Property.parse(parser).isEmpty())

        // we're now at the end of <multistatus>
        assertEquals(XmlPullParser.END_TAG, parser.eventType)
        assertEquals("multistatus", parser.name)
    }

    @Test
    fun testParse_ValidProperty() {
        val parser = XmlPullParserFactory.newInstance().apply {
            isNamespaceAware = true
        }.newPullParser()
        parser.setInput(StringReader("<multistatus xmlns='DAV:'><getetag>12345</getetag></multistatus>"))
        do {
            parser.next()
        } while (parser.eventType != XmlPullParser.START_TAG && parser.name != "multistatus")

        // we're now at the start of <multistatus>
        assertEquals(XmlPullParser.START_TAG, parser.eventType)
        assertEquals("multistatus", parser.name)

        val etag = Property.parse(parser).first()
        assertEquals(GetETag("12345"), etag)

        // we're now at the end of <multistatus>
        assertEquals(XmlPullParser.END_TAG, parser.eventType)
        assertEquals("multistatus", parser.name)
    }

}