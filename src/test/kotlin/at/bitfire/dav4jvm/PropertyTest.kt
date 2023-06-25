/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm

import at.bitfire.dav4jvm.property.GetETag
import nl.adaptivity.xmlutil.EventType
import nl.adaptivity.xmlutil.toCName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PropertyTest {

    @Test
    fun testParse_InvalidProperty() {
        val parser = XmlUtils.createReader("<multistatus xmlns='DAV:'><getetag/></multistatus>")
        parser.next()

        // we're now at the start of <multistatus>
        assertEquals(EventType.START_ELEMENT, parser.eventType)
        assertEquals("multistatus", parser.name.toCName())

        // parse invalid DAV:getetag
        assertTrue(Property.parse(parser).isEmpty())

        // we're now at the end of <multistatus>
        assertEquals(EventType.END_ELEMENT, parser.eventType)
        assertEquals("multistatus", parser.name.toCName())
    }

    @Test
    fun testParse_ValidProperty() {
        val parser = XmlUtils.createReader("<multistatus xmlns='DAV:'><getetag>12345</getetag></multistatus>")
        parser.next()

        // we're now at the start of <multistatus>
        assertEquals(EventType.START_ELEMENT, parser.eventType)
        assertEquals("multistatus", parser.name.toCName())

        val etag = Property.parse(parser).first()
        assertEquals(GetETag("12345"), etag)

        // we're now at the end of <multistatus>
        assertEquals(EventType.END_ELEMENT, parser.eventType)
        assertEquals("multistatus", parser.name.toCName())
    }
}
