package at.bitfire.dav4jvm.property

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GetETagTest: PropertyTest() {

    @Test
    fun testGetETag_NoText() {
        val results = parseProperty("<getetag xmlns=\"DAV:\"><invalid/></getetag>")
        val getETag = results.first() as GetETag
        assertNull(getETag.eTag)
        assertNull(getETag.weak)
    }

    @Test
    fun testGetETag_Strong() {
        val results = parseProperty("<getetag xmlns=\"DAV:\">\"Correct strong ETag\"</getetag>")
        val getETag = results.first() as GetETag
        assertEquals("Correct strong ETag", getETag.eTag)
        assert(getETag.weak == false)
    }

    @Test
    fun testGetETag_Strong_NoQuotes() {
        val results = parseProperty("<getetag xmlns=\"DAV:\">Strong ETag without quotes</getetag>")
        val getETag = results.first() as GetETag
        assertEquals("Strong ETag without quotes", getETag.eTag)
        assert(getETag.weak == false)
    }

    @Test
    fun testGetETag_Weak() {
        val results = parseProperty("<getetag xmlns=\"DAV:\">W/\"Correct weak ETag\"</getetag>")
        val getETag = results.first() as GetETag
        assertEquals("Correct weak ETag", getETag.eTag)
        assert(getETag.weak == true)
    }

    @Test
    fun testGetETag_Weak_NoQuotes() {
        val results = parseProperty("<getetag xmlns=\"DAV:\">W/Weak ETag without quotes</getetag>")
        val getETag = results.first() as GetETag
        assertEquals("Weak ETag without quotes", getETag.eTag)
        assert(getETag.weak == true)
    }

}