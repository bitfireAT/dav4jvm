package at.bitfire.dav4jvm.property

import at.bitfire.dav4jvm.property.webdav.GetETag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GetETagTest: PropertyTest() {

    @Test
    fun testGetETag_Strong() {
        val results = parseProperty("<getetag xmlns=\"DAV:\">\"Correct strong ETag\"</getetag>")
        val getETag = results.first() as GetETag
        assertEquals("Correct strong ETag", getETag.eTag)
        assertFalse(getETag.weak)
    }

    @Test
    fun testGetETag_Strong_NoQuotes() {
        val results = parseProperty("<getetag xmlns=\"DAV:\">Strong ETag without quotes</getetag>")
        val getETag = results.first() as GetETag
        assertEquals("Strong ETag without quotes", getETag.eTag)
        assertFalse(getETag.weak)
    }

    @Test
    fun testGetETag_Weak() {
        val results = parseProperty("<getetag xmlns=\"DAV:\">W/\"Correct weak ETag\"</getetag>")
        val getETag = results.first() as GetETag
        assertEquals("Correct weak ETag", getETag.eTag)
        assertTrue(getETag.weak)
    }

    @Test
    fun testGetETag_Weak_Empty() {
        val results = parseProperty("<getetag xmlns=\"DAV:\">W/</getetag>")
        val getETag = results.first() as GetETag
        assertEquals("", getETag.eTag)
        assertTrue(getETag.weak)
    }

    @Test
    fun testGetETag_Weak_NoQuotes() {
        val results = parseProperty("<getetag xmlns=\"DAV:\">W/Weak ETag without quotes</getetag>")
        val getETag = results.first() as GetETag
        assertEquals("Weak ETag without quotes", getETag.eTag)
        assertTrue(getETag.weak)
    }

}