/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

package at.bitfire.dav4jvm.ktor

import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.XmlUtils
import at.bitfire.dav4jvm.XmlUtils.insertTag
import at.bitfire.dav4jvm.property.caldav.CalendarData
import at.bitfire.dav4jvm.property.caldav.NS_CALDAV
import at.bitfire.dav4jvm.property.caldav.ScheduleTag
import at.bitfire.dav4jvm.property.common.HrefListProperty
import at.bitfire.dav4jvm.property.webdav.GetContentType
import at.bitfire.dav4jvm.property.webdav.GetETag
import at.bitfire.dav4jvm.property.webdav.NS_WEBDAV
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.util.logging.*
import org.slf4j.LoggerFactory
import java.io.StringWriter
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*

@Suppress("unused")
class DavCalendar @JvmOverloads constructor(
    httpClient: HttpClient,
    location: Url,
    logger: Logger = LoggerFactory.getLogger(DavCalendar::javaClass.name)
): DavCollection(httpClient, location, logger) {

    companion object {
        val MIME_ICALENDAR = ContentType.Companion.parse("text/calendar")
        val MIME_ICALENDAR_UTF8 = ContentType.Companion.parse("text/calendar;charset=utf-8")

        val CALENDAR_QUERY = Property.Name(NS_CALDAV, "calendar-query")
        val CALENDAR_MULTIGET = Property.Name(NS_CALDAV, "calendar-multiget")

        val FILTER = Property.Name(NS_CALDAV, "filter")
        val COMP_FILTER = Property.Name(NS_CALDAV, "comp-filter")
        const val COMP_FILTER_NAME = "name"
        val TIME_RANGE = Property.Name(NS_CALDAV, "time-range")
        const val TIME_RANGE_START = "start"
        const val TIME_RANGE_END = "end"

        private val timeFormatUTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssVV", Locale.US)
    }


    /**
     * Sends a calendar-query REPORT to the resource.
     *
     * @param component requested component name (like VEVENT or VTODO)
     * @param start     time-range filter: start date (optional)
     * @param end       time-range filter: end date (optional)
     * @param callback  called for every WebDAV response XML element in the result
     *
     * @return list of properties which have been received in the Multi-Status response, but
     * are not part of response XML elements
     *
     * @throws java.io.IOException on I/O error
     * @throws at.bitfire.dav4jvm.ktor.exception.HttpException on HTTP error
     * @throws at.bitfire.dav4jvm.ktor.exception.DavException on WebDAV error
     */
    suspend fun calendarQuery(component: String, start: Instant?, end: Instant?, callback: MultiResponseCallback): List<Property> {
        /* <!ELEMENT calendar-query ((DAV:allprop |
                                      DAV:propname |
                                      DAV:prop)?, filter, timezone?)>
           <!ELEMENT filter (comp-filter)>
           <!ELEMENT comp-filter (is-not-defined | (time-range?,
                                  prop-filter*, comp-filter*))>
           <!ATTLIST comp-filter name CDATA #REQUIRED>
           name value: a calendar object or calendar component
                       type (e.g., VEVENT)

        */
        val serializer = XmlUtils.newSerializer()
        val writer = StringWriter()
        serializer.setOutput(writer)
        serializer.startDocument("UTF-8", null)
        serializer.setPrefix("", NS_WEBDAV)
        serializer.setPrefix("CAL", NS_CALDAV)
        serializer.insertTag(CALENDAR_QUERY) {
            insertTag(PROP) {
                insertTag(GetETag.Companion.NAME)
            }
            insertTag(FILTER) {
                insertTag(COMP_FILTER) {
                    attribute(null, COMP_FILTER_NAME, "VCALENDAR")
                    insertTag(COMP_FILTER) {
                        attribute(null, COMP_FILTER_NAME, component)
                        if (start != null || end != null) {
                            insertTag(TIME_RANGE) {
                                if (start != null)
                                    attribute(null, TIME_RANGE_START, timeFormatUTC.format(
                                        ZonedDateTime.ofInstant(start, ZoneOffset.UTC)
                                    ))
                                if (end != null)
                                    attribute(null, TIME_RANGE_END, timeFormatUTC.format(
                                        ZonedDateTime.ofInstant(end, ZoneOffset.UTC)
                                    ))
                            }
                        }
                    }
                }
            }
        }
        serializer.endDocument()

        followRedirects {
            httpClient.prepareRequest {
                url(location)
                method = HttpMethod.Companion.parse("REPORT")
                setBody(writer.toString())
                headers.append(HttpHeaders.ContentType, MIME_XML.toString())
                headers.append(HttpHeaders.Depth, "1")
            }.execute()
        }.let { response ->
            return processMultiStatus(response, callback)
        }
    }

    /**
     * Sends a calendar-multiget REPORT to the resource. Received responses are sent
     * to the callback, whether they are successful (2xx) or not.
     *
     * @param urls         list of iCalendar URLs to be requested
     * @param contentType  MIME type of requested format; may be "text/calendar" for iCalendar or
     *                     "application/calendar+json" for jCard. *null*: don't request specific representation type
     * @param version      Version subtype of the requested format, like "2.0" for iCalendar 2. *null*: don't request specific version
     * @param callback     called for every WebDAV response XML element in the result
     *
     * @return list of properties which have been received in the Multi-Status response, but
     * are not part of response XML elements
     *
     * @throws java.io.IOException on I/O error
     * @throws at.bitfire.dav4jvm.ktor.exception.HttpException on HTTP error
     * @throws at.bitfire.dav4jvm.ktor.exception.DavException on WebDAV error
     */
    suspend fun multiget(urls: List<Url>, contentType: String? = null, version: String? = null, callback: MultiResponseCallback): List<Property> {
        /* <!ELEMENT calendar-multiget ((DAV:allprop |
                                        DAV:propname |
                                        DAV:prop)?, DAV:href+)>
        */
        val serializer = XmlUtils.newSerializer()
        val writer = StringWriter()
        serializer.setOutput(writer)
        serializer.startDocument("UTF-8", null)
        serializer.setPrefix("", NS_WEBDAV)
        serializer.setPrefix("CAL", NS_CALDAV)
        serializer.insertTag(CALENDAR_MULTIGET) {
            insertTag(PROP) {
                insertTag(GetContentType.Companion.NAME)     // to determine the character set
                insertTag(GetETag.Companion.NAME)
                insertTag(ScheduleTag.Companion.NAME)
                insertTag(CalendarData.Companion.NAME) {
                    if (contentType != null)
                        attribute(null, CalendarData.Companion.CONTENT_TYPE, contentType)
                    if (version != null)
                        attribute(null, CalendarData.Companion.VERSION, version)
                }
            }
            for (url in urls)
                insertTag(HrefListProperty.HREF) {
                    serializer.text(url.encodedPath)
                }
        }
        serializer.endDocument()

        followRedirects {
            httpClient.prepareRequest {
                url(location)
                method = HttpMethod.Companion.parse("REPORT")
                setBody(writer.toString())
                headers.append(HttpHeaders.ContentType, MIME_XML.toString())
            }.execute()
        }.let { response ->
            return processMultiStatus(response, callback)
        }
    }

}