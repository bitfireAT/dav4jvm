package at.bitfire.dav4jvm.property

import kotlin.test.assertEquals
import kotlin.test.assertNull

object OwnerTest : PropertyTest() {

    init {
        test("testOwner_PlainText") {
            val results = parseProperty("<owner xmlns=\"DAV:\">https://example.com</owner>")
            val owner = results.first() as Owner
            assertNull(owner.href)
        }

        test("testOwner_PlainTextAndHref") {
            val results =
                parseProperty("<owner xmlns=\"DAV:\">Principal Name. <href>mailto:owner@example.com</href> (test)</owner>")
            val owner = results.first() as Owner
            assertEquals("mailto:owner@example.com", owner.href)
        }

        test("testOwner_Href") {
            val results = parseProperty("<owner xmlns=\"DAV:\"><href>https://example.com</href></owner>")
            val owner = results.first() as Owner
            assertEquals("https://example.com", owner.href)
        }
    }

}