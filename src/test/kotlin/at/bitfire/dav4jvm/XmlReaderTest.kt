package at.bitfire.dav4jvm

import org.junit.Assert.*
import org.junit.Test
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader

class XmlReaderTest {
    @Test
    fun testProcessTagRoot() {
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
    fun testProcessTagDepth1() {
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
    fun testReadTextCDATA() {
        val parser = XmlUtils.newPullParser()
        parser.setInput(StringReader("<test><![CDATA[Test 1</test><test><garbage/>Test 2]]></test>"))
        parser.next()       // now on START_TAG <test>

        assertEquals("Test 1</test><test><garbage/>Test 2", XmlReader(parser).readText())
        assertEquals(XmlPullParser.END_TAG, parser.eventType)
    }

    @Test
    fun testReadTextPropertyRoot() {
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
    fun testReadTextPropertyListDepth1() {
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
    fun testReadLongOrNull() {
        val parser = XmlUtils.newPullParser()
        parser.setInput(StringReader("<root><test>1</test><test><garbage/>2</test><test><garbage/>a</test></root>"))
        parser.next()
        parser.next()       // now on START_TAG <test>
        val reader = XmlReader(parser)

        assertEquals(1L, reader.readLongOrNull())
        assertEquals(XmlPullParser.END_TAG, parser.eventType)
        parser.next()

        assertEquals(2L, reader.readLongOrNull())
        assertEquals(XmlPullParser.END_TAG, parser.eventType)
        parser.next()

        assertNull(reader.readLongOrNull())
        assertEquals(XmlPullParser.END_TAG, parser.eventType)
    }
}
