package at.bitfire.dav4jvm.property

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

object GetETagTest : PropertyTest() {

    init {
        test("testGetETag_Strong") {
            val results = parseProperty("<getetag xmlns=\"DAV:\">\"Correct strong ETag\"</getetag>")
            val getETag = results.first() as GetETag
            assertEquals("Correct strong ETag", getETag.eTag)
            assertFalse(getETag.weak)
        }

        test("testGetETag_Strong_NoQuotes") {
            val results = parseProperty("<getetag xmlns=\"DAV:\">Strong ETag without quotes</getetag>")
            val getETag = results.first() as GetETag
            assertEquals("Strong ETag without quotes", getETag.eTag)
            assertFalse(getETag.weak)
        }

        test("testGetETag_Weak") {
            val results = parseProperty("<getetag xmlns=\"DAV:\">W/\"Correct weak ETag\"</getetag>")
            val getETag = results.first() as GetETag
            assertEquals("Correct weak ETag", getETag.eTag)
            assertTrue(getETag.weak)
        }

        test("testGetETag_Weak_Empty") {
            val results = parseProperty("<getetag xmlns=\"DAV:\">W/</getetag>")
            val getETag = results.first() as GetETag
            assertEquals("", getETag.eTag)
            assertTrue(getETag.weak)
        }

        test("testGetETag_Weak_NoQuotes") {
            val results = parseProperty("<getetag xmlns=\"DAV:\">W/Weak ETag without quotes</getetag>")
            val getETag = results.first() as GetETag
            assertEquals("Weak ETag without quotes", getETag.eTag)
            assertTrue(getETag.weak)
        }
    }

}