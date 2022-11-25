/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.kobjects.ktxml.api.EventType
import org.kobjects.ktxml.mini.MiniXmlPullParser
import java.io.StringReader

class XmlUtilsTest {

    @Test
    fun testProcessTagRoot() {
        val parser = MiniXmlPullParser(StringReader("<test></test>").toString().iterator())
        // now on START_DOCUMENT [0]

        var processed = false
        XmlUtils.processTag(parser, Property.Name("", "test")) {
            processed = true
        }
        assertTrue(processed)
    }

    @Test
    fun testProcessTagDepth1() {
        val parser = MiniXmlPullParser(StringReader("<root><test></test></root>").toString().iterator())
        parser.next()       // now on START_TAG <root>

        var processed = false
        XmlUtils.processTag(parser, Property.Name("", "test")) {
            processed = true
        }
        assertTrue(processed)
    }

    @Test
    fun testReadText() {
        val parser = MiniXmlPullParser(StringReader("<root><test>Test 1</test><test><garbage/>Test 2</test></root>").toString().iterator())
        parser.next()
        parser.next()       // now on START_TAG <test>

        assertEquals("Test 1", XmlUtils.readText(parser))
        assertEquals(EventType.END_TAG, parser.eventType)
        parser.next()

        assertEquals("Test 2", XmlUtils.readText(parser))
        assertEquals(EventType.END_TAG, parser.eventType)
    }

    @Test
    fun testReadTextCDATA() {
        val parser = MiniXmlPullParser(StringReader("<test><![CDATA[Test 1</test><test><garbage/>Test 2]]></test>").toString().iterator())
        parser.next()       // now on START_TAG <test>

        assertEquals("Test 1</test><test><garbage/>Test 2", XmlUtils.readText(parser))
        assertEquals(EventType.END_TAG, parser.eventType)
    }

    @Test
    fun testReadTextPropertyRoot() {
        val parser = MiniXmlPullParser(StringReader("<root><entry>Test 1</entry><entry>Test 2</entry></root>").toString().iterator())
        parser.next()        // now on START_TAG <root>

        val entries = mutableListOf<String>()
        XmlUtils.readTextPropertyList(parser, Property.Name("", "entry"), entries)
        assertEquals("Test 1", entries[0])
        assertEquals("Test 2", entries[1])

        parser.next()       // END_TAG </root>
        assertEquals(EventType.END_DOCUMENT, parser.eventType)
    }

    @Test
    fun testReadTextPropertyListDepth1() {
        val parser = MiniXmlPullParser(StringReader("<test><entry>Test 1</entry><entry>Test 2</entry></test>").toString().iterator())
        parser.next()       // now on START_TAG <test> [1]

        val entries = mutableListOf<String>()
        XmlUtils.readTextPropertyList(parser, Property.Name("", "entry"), entries)
        assertEquals("Test 1", entries[0])
        assertEquals("Test 2", entries[1])
        assertEquals(EventType.END_TAG, parser.eventType)
        assertEquals("test", parser.name)
    }

}