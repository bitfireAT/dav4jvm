/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.dav4android

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
        XmlUtils.processTag(parser, "", "test", {
            processed = true
        })
        assertTrue(processed)
    }

    @Test
    fun testProcessTagDepth1() {
        val parser = XmlUtils.newPullParser()
        parser.setInput(StringReader("<root><test></test></root>"))
        parser.next()       // now on START_TAG <root> [1]

        var processed = false
        XmlUtils.processTag(parser, "", "test", {
            processed = true
        })
        assertTrue(processed)
    }

    @Test
    fun testReadText() {
        val parser = XmlUtils.newPullParser()
        parser.setInput(StringReader("<test>Test 1</test><test><garbage/>Test 2</test>"))
        parser.next()       // now on START_TAG <test> [1]

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
        parser.next()       // now on START_TAG <test> [1]

        assertEquals("Test 1</test><test><garbage/>Test 2", XmlUtils.readText(parser))
        assertEquals(XmlPullParser.END_TAG, parser.eventType)
    }

    @Test
    fun testReadTextPropertyRoot() {
        val parser = XmlUtils.newPullParser()
        parser.setInput(StringReader("<entry>Test 1</entry><entry>Test 2</entry>"))
        // now on START_DOCUMENT [0]

        val entries = mutableListOf<String>()
        XmlUtils.readTextPropertyList(parser, Property.Name("", "entry"), entries)
        assertEquals("Test 1", entries[0])
        assertEquals("Test 2", entries[1])
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