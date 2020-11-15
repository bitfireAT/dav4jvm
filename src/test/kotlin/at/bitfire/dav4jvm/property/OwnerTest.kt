package at.bitfire.dav4jvm.property

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OwnerTest: PropertyTest() {

    @Test
    fun testOwner_PlainText() {
        val results = parseProperty("<owner xmlns=\"DAV:\">https://example.com</owner>")
        val owner = results.first() as Owner
        assertNull(owner.href)
    }

    @Test
    fun testOwner_PlainTextAndHref() {
        val results = parseProperty("<owner xmlns=\"DAV:\">Principal Name. <href>mailto:owner@example.com</href> (test)</owner>")
        val owner = results.first() as Owner
        assertEquals("mailto:owner@example.com", owner.href)
    }

    @Test
    fun testOwner_Href() {
        val results = parseProperty("<owner xmlns=\"DAV:\"><href>https://example.com</href></owner>")
        val owner = results.first() as Owner
        assertEquals("https://example.com", owner.href)
    }

}