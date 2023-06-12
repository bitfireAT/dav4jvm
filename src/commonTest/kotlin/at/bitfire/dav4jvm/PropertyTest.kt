/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm

import at.bitfire.dav4jvm.property.GetETag
import io.kotest.core.spec.style.FunSpec
import nl.adaptivity.xmlutil.EventType
import nl.adaptivity.xmlutil.XmlStreaming
import nl.adaptivity.xmlutil.localPart
import kotlin.test.assertEquals
import kotlin.test.assertTrue

object PropertyTest : FunSpec({

    test("testParse_InvalidProperty") {
        val parser = XmlStreaming.newReader("<multistatus xmlns='DAV:'><getetag/></multistatus>")
        do {
            parser.next()
        } while (parser.eventType != EventType.START_ELEMENT && parser.name.localPart != "multistatus")

        // we're now at the start of <multistatus>
        assertEquals(EventType.START_ELEMENT, parser.eventType)
        assertEquals("multistatus", parser.name.localPart)

        // parse invalid DAV:getetag
        assertTrue(Property.parse(parser).isEmpty())

        // we're now at the end of <multistatus>
        assertEquals(EventType.END_ELEMENT, parser.eventType)
        assertEquals("multistatus", parser.name.localPart)
    }

    test("testParse_ValidProperty") {
        val parser = XmlStreaming.newReader("<multistatus xmlns='DAV:'><getetag>12345</getetag></multistatus>")
        do {
            parser.next()
        } while (parser.eventType != EventType.START_ELEMENT && parser.name.localPart != "multistatus")

        // we're now at the start of <multistatus>
        assertEquals(EventType.START_ELEMENT, parser.eventType)
        assertEquals("multistatus", parser.name.localPart)

        val etag = Property.parse(parser).first()
        assertEquals(GetETag("12345"), etag)

        // we're now at the end of <multistatus>
        assertEquals(EventType.END_ELEMENT, parser.eventType)
        assertEquals("multistatus", parser.name.localPart)
    }

})