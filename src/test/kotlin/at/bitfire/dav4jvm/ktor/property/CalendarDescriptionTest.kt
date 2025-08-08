/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

package at.bitfire.dav4jvm.ktor.property

import at.bitfire.dav4jvm.ktor.property.caldav.CalendarDescription
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