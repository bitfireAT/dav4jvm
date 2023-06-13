/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm

import io.kotest.core.spec.style.FunSpec
import nl.adaptivity.xmlutil.EventType
import nl.adaptivity.xmlutil.QName
import nl.adaptivity.xmlutil.XmlReader
import nl.adaptivity.xmlutil.localPart
import kotlin.test.assertEquals
import kotlin.test.assertTrue

object XmlUtilsTest : FunSpec({

    fun advanceTo(
        parser: XmlReader,
        eventType: EventType? = null,
        name: QName? = null
    ) {
        if (eventType == name) throw IllegalStateException("Both can't be null")
        while ((name == null || !(parser.isStartElement() || parser.isEndElement()) || parser.name != name) && (eventType == null || parser.eventType != eventType))
            parser.next()
    }

    test("testProcessTagRoot") {
        val parser = XmlUtils.createReader("<test></test>")
        // now on START_DOCUMENT [0]

        var processed = false
        XmlUtils.processTag(parser, QName("", "test")) {
            processed = true
        }
        assertTrue(processed)
    }

    test("testProcessTagDepth1") {
        val parser = XmlUtils.createReader("<root><test></test></root>")
        parser.next()      // now on START_TAG <root>

        var processed = false
        XmlUtils.processTag(parser, QName("", "test")) {
            processed = true
        }
        assertTrue(processed)
    }

    test("testReadText") {
        val parser = XmlUtils.createReader("<root><test>Test 1</test><test><garbage/>Test 2</test></root>")
        parser.next()
        parser.next()       // now on START_TAG <test>

        assertEquals("Test 1", XmlUtils.readText(parser))
        assertEquals(EventType.END_ELEMENT, parser.eventType)
        parser.next()

        assertEquals("Test 2", XmlUtils.readText(parser))
        assertEquals(EventType.END_ELEMENT, parser.eventType)
    }

    test("testReadTextCDATA") {
        val parser = XmlUtils.createReader("<test><![CDATA[Test 1</test><test><garbage/>Test 2]]></test>")
        parser.next()       // now on START_TAG <test>

        assertEquals("Test 1</test><test><garbage/>Test 2", XmlUtils.readText(parser))
        assertEquals(EventType.END_ELEMENT, parser.eventType)
    }

    test("testReadTextPropertyRoot") {
        val parser = XmlUtils.createReader("<root><entry>Test 1</entry><entry>Test 2</entry></root>")
        parser.next()         // now on START_TAG <root>

        val entries = mutableListOf<String>()
        XmlUtils.readTextPropertyList(parser, QName("", "entry"), entries)
        assertEquals("Test 1", entries[0])
        assertEquals("Test 2", entries[1])

        parser.next()       // END_TAG </root>
        assertEquals(EventType.END_DOCUMENT, parser.eventType)
    }

    test("testReadTextPropertyListDepth1") {
        val parser = XmlUtils.createReader("<test><entry>Test 1</entry><entry>Test 2</entry></test>")
        parser.next()       // now on START_TAG <test> [1]

        val entries = mutableListOf<String>()
        XmlUtils.readTextPropertyList(parser, QName("", "entry"), entries)
        assertEquals("Test 1", entries[0])
        assertEquals("Test 2", entries[1])
        assertEquals(EventType.END_ELEMENT, parser.eventType)
        assertEquals("test", parser.name.localPart)
    }
})