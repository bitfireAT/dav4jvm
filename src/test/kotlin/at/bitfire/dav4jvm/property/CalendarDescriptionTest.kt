package at.bitfire.dav4jvm.property

import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarDescriptionTest: PropertyTest() {

    @Test
    fun testCalendarDescription() {
        val results = parseProperty("<calendar-description xmlns=\"urn:ietf:params:xml:ns:caldav\">My Calendar</calendar-description>")
        val result = results.first() as CalendarDescription
        assertEquals("My Calendar", result.description)
    }

}