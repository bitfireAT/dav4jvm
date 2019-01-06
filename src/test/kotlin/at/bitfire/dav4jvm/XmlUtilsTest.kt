/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader

class XmlUtilsTest {

    @Test
    fun testProcessTagRoot() {
        val parser = XmlUtils.newPullParser()
        parser.setInput(StringReader("<test></test>"))
        // now on START_DOCUMENT [0]

        var processed = false
        XmlUtils.processTag(parser, "", "test") {
            processed = true
        }
        assertTrue(processed)
    }

    @Test
    fun testProcessTagDepth1() {
        val parser = XmlUtils.newPullParser()
        parser.setInput(StringReader("<root><test></test></root>"))
        parser.next()       // now on START_TAG <root>

        var processed = false
        XmlUtils.processTag(parser, "", "test") {
            processed = true
        }
        assertTrue(processed)
    }

    @Test
    fun testReadText() {
        val parser = XmlUtils.newPullParser()
        parser.setInput(StringReader("<root><test>Test 1</test><test><garbage/>Test 2</test></root>"))
        parser.next()
        parser.next()       // now on START_TAG <test>

        assertEquals("Test 1", XmlUtils.readText(parser))
        assertEquals(XmlPullParser.END_TAG, parser.eventType)
        parser.next()

        assertEquals("Test 2", XmlUtils.readText(parser))
        assertEquals(XmlPullParser.END_TAG, parser.eventType)
    }

    @Test
    fun testReadTextCDATA() {
        val parser = XmlUtils.newPullParser()
        parser.setInput(StringReader("<test><![CDATA[Test 1</test><test><garbage/>Test 2]]></test>"))
        parser.next()       // now on START_TAG <test>

        assertEquals("Test 1</test><test><garbage/>Test 2", XmlUtils.readText(parser))
        assertEquals(XmlPullParser.END_TAG, parser.eventType)
    }

    @Test
    fun testReadTextPropertyRoot() {
        val parser = XmlUtils.newPullParser()
        parser.setInput(StringReader("<root><entry>Test 1</entry><entry>Test 2</entry></root>"))
        parser.next()        // now on START_TAG <root>

        val entries = mutableListOf<String>()
        XmlUtils.readTextPropertyList(parser, Property.Name("", "entry"), entries)
        assertEquals("Test 1", entries[0])
        assertEquals("Test 2", entries[1])

        parser.next()       // END_TAG </root>
        assertEquals(XmlPullParser.END_DOCUMENT, parser.eventType)
    }

    @Test
    fun testReadTextPropertyListDepth1() {
        val parser = XmlUtils.newPullParser()
        parser.setInput(StringReader("<test><entry>Test 1</entry><entry>Test 2</entry></test>"))
        parser.next()       // now on START_TAG <test> [1]

        val entries = mutableListOf<String>()
        XmlUtils.readTextPropertyList(parser, Property.Name("", "entry"), entries)
        assertEquals("Test 1", entries[0])
        assertEquals("Test 2", entries[1])
        assertEquals(XmlPullParser.END_TAG, parser.eventType)
        assertEquals("test", parser.name)
    }

}