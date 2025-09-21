/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

package at.bitfire.dav4jvm

import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.time.Instant

class XmlReaderTest {

    @Test
    fun testProcessTag_Root() {
        val parser = XmlUtils.newPullParser()
        parser.setInput(StringReader("<test></test>"))
        // now on START_DOCUMENT [0]

        var processed = false
        XmlReader(parser).processTag(Property.Name("", "test")) {
            processed = true
        }
        assertTrue(processed)
    }

    @Test
    fun testProcessTag_Depth1() {
        val parser = XmlUtils.newPullParser()
        parser.setInput(StringReader("<root><test></test></root>"))
        parser.next()       // now on START_TAG <root>

        var processed = false
        XmlReader(parser).processTag(Property.Name("", "test")) {
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
        val reader = XmlReader(parser)

        assertEquals("Test 1", reader.readText())
        assertEquals(XmlPullParser.END_TAG, parser.eventType)
        parser.next()

        assertEquals("Test 2", reader.readText())
        assertEquals(XmlPullParser.END_TAG, parser.eventType)
    }

    @Test
    fun testReadText_CDATA() {
        val parser = XmlUtils.newPullParser()
        parser.setInput(StringReader("<test><![CDATA[Test 1</test><test><garbage/>Test 2]]></test>"))
        parser.next()       // now on START_TAG <test>

        assertEquals("Test 1</test><test><garbage/>Test 2", XmlReader(parser).readText())
        assertEquals(XmlPullParser.END_TAG, parser.eventType)
    }

    @Test
    fun testReadText_PropertyRoot() {
        val parser = XmlUtils.newPullParser()
        parser.setInput(StringReader("<root><entry>Test 1</entry><entry>Test 2</entry></root>"))
        parser.next()        // now on START_TAG <root>

        val entries = mutableListOf<String>()
        XmlReader(parser).readTextPropertyList(Property.Name("", "entry"), entries)
        assertEquals("Test 1", entries[0])
        assertEquals("Test 2", entries[1])

        parser.next()       // END_TAG </root>
        assertEquals(XmlPullParser.END_DOCUMENT, parser.eventType)
    }


    @Test
    fun testReadTextPropertyList_Depth1() {
        val parser = XmlUtils.newPullParser()
        parser.setInput(StringReader("<test><entry>Test 1</entry><entry>Test 2</entry></test>"))
        parser.next()       // now on START_TAG <test> [1]

        val entries = mutableListOf<String>()
        XmlReader(parser).readTextPropertyList(Property.Name("", "entry"), entries)
        assertEquals("Test 1", entries[0])
        assertEquals("Test 2", entries[1])
        assertEquals(XmlPullParser.END_TAG, parser.eventType)
        assertEquals("test", parser.name)
    }


    @Test
    fun testReadLong() {
        val parser = XmlUtils.newPullParser()
        parser.setInput(StringReader("<root><test>1</test><test><garbage/>2</test><test><garbage/>a</test></root>"))
        parser.next()
        parser.next()       // now on START_TAG <test>
        val reader = XmlReader(parser)

        assertEquals(1L, reader.readLong())
        assertEquals(XmlPullParser.END_TAG, parser.eventType)
        parser.next()

        assertEquals(2L, reader.readLong())
        assertEquals(XmlPullParser.END_TAG, parser.eventType)
        parser.next()

        assertNull(reader.readLong())
        assertEquals(XmlPullParser.END_TAG, parser.eventType)
    }


    @Test
    fun testReadHttpDate() {
        val parser = XmlUtils.newPullParser()
        parser.setInput(StringReader("<root><test>Sun, 06 Nov 1994 08:49:37 GMT</test><test><garbage/>Sun, 06 Nov 1994 08:49:37 GMT</test><test><garbage/>invalid</test></root>"))
        parser.next()
        parser.next()       // now on START_TAG <test>
        val reader = XmlReader(parser)

        assertEquals(Instant.ofEpochSecond(784111777), reader.readHttpDate())
        assertEquals(XmlPullParser.END_TAG, parser.eventType)
        parser.next()

        assertEquals(Instant.ofEpochSecond(784111777), reader.readHttpDate())
        assertEquals(XmlPullParser.END_TAG, parser.eventType)
        parser.next()

        assertNull(reader.readHttpDate())
        assertEquals(XmlPullParser.END_TAG, parser.eventType)
    }


    @Test
    fun testReadContentTypes() {
        val parser = XmlUtils.newPullParser()
        parser.setInput(StringReader("<root><test content-type=\"text/plain\">text</test><test content-type=\"application/json\">{}</test></root>"))
        parser.next()
        val reader = XmlReader(parser)

        val types = mutableListOf<String>()
        reader.readContentTypes(Property.Name("", "test"), types::add)
        assertEquals(2, types.size)
        assertEquals("text/plain", types[0])
        assertEquals("application/json", types[1])
    }

}