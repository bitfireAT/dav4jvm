/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

package at.bitfire.dav4jvm.property.caldav

import at.bitfire.dav4jvm.Property

object CalDAV {

    // CalDAV (RFC 4791)

    const val NS_CALDAV = "urn:ietf:params:xml:ns:caldav"

    val AllComp = Property.Name(NS_CALDAV, "allcomp")
    val Calendar = Property.Name(NS_CALDAV, "calendar")
    val Comp = Property.Name(NS_CALDAV, "comp")
    val CalendarData = Property.Name(NS_CALDAV, "calendar-data")
    val CalendarDescription = Property.Name(NS_CALDAV, "calendar-description")
    val CalendarHomeSet = Property.Name(NS_CALDAV, "calendar-home-set")
    val CalendarMultiget = Property.Name(NS_CALDAV, "calendar-multiget")
    val CalendarQuery = Property.Name(NS_CALDAV, "calendar-query")
    val CalendarTimezone = Property.Name(NS_CALDAV, "calendar-timezone")
    val CompFilter = Property.Name(NS_CALDAV, "comp-filter")
    val Filter = Property.Name(NS_CALDAV, "filter")
    val MaxResourceSize = Property.Name(NS_CALDAV, "max-resource-size")
    val SupportedCalendarComponentSet = Property.Name(NS_CALDAV, "supported-calendar-component-set")
    val SupportedCalendarData = Property.Name(NS_CALDAV, "supported-calendar-data")
    val TimeRange = Property.Name(NS_CALDAV, "time-range")


    // Scheduling Extensions to CalDAV (RFC 6638)

    val CalendarUserAddressSet = Property.Name(NS_CALDAV, "calendar-user-address-set")
    val ScheduleTag = Property.Name(NS_CALDAV, "schedule-tag")


    // Calendaring Extensions to WebDAV (CalDAV): Time Zones by Reference (RFC 7809)

    val CalendarTimezoneId = Property.Name(NS_CALDAV, "calendar-timezone-id")


    // Apple XML elements

    const val NS_APPLE_ICAL = "http://apple.com/ns/ical/"

    val CalendarColor = Property.Name(NS_APPLE_ICAL, "calendar-color")


    // CalendarServer XML elements

    const val NS_CALENDARSERVER = "http://calendarserver.org/ns/"

    val CalendarProxyRead = Property.Name(NS_CALENDARSERVER, "calendar-proxy-read")
    val CalendarProxyReadFor = Property.Name(NS_CALENDARSERVER, "calendar-proxy-read-for")
    val CalendarProxyWrite = Property.Name(NS_CALENDARSERVER, "calendar-proxy-write")
    val CalendarProxyWriteFor = Property.Name(NS_CALENDARSERVER, "calendar-proxy-write-for")
    val GetCTag = Property.Name(NS_CALENDARSERVER, "getctag")
    val Source = Property.Name(NS_CALENDARSERVER, "source")
    val Subscribed = Property.Name(NS_CALENDARSERVER, "subscribed")

}