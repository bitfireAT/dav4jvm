package at.bitfire.dav4jvm.property

import kotlin.test.assertEquals

object CalendarDescriptionTest : PropertyTest() {

    init {
        test("testCalendarDescription") {
            val results =
                parseProperty("<calendar-description xmlns=\"urn:ietf:params:xml:ns:caldav\">My Calendar</calendar-description>")
            val result = results.first() as CalendarDescription
            assertEquals("My Calendar", result.description)
        }
    }

}