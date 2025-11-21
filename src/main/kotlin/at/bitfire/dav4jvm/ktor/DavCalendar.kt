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
import at.bitfire.dav4jvm.property.caldav.CalDAV
import at.bitfire.dav4jvm.property.caldav.CalendarData
import at.bitfire.dav4jvm.property.webdav.WebDAV
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.prepareRequest
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.util.logging.Logger
import org.slf4j.LoggerFactory
import java.io.StringWriter
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Suppress("unused")
class DavCalendar(
    httpClient: HttpClient,
    location: Url,
    logger: Logger = LoggerFactory.getLogger(DavCalendar::javaClass.name)
): DavCollection(httpClient, location, logger) {

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
    suspend fun calendarQuery(
        component: String,
        start: Instant?,
        end: Instant?,
        callback: MultiResponseCallback
    ): List<Property> {
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
        serializer.setPrefix("", WebDAV.NS_WEBDAV)
        serializer.setPrefix("CAL", CalDAV.NS_CALDAV)
        serializer.insertTag(CalDAV.CalendarQuery) {
            insertTag(WebDAV.Prop) {
                insertTag(WebDAV.GetETag)
            }
            insertTag(CalDAV.Filter) {
                insertTag(CalDAV.CompFilter) {
                    attribute(null, COMP_FILTER_NAME, "VCALENDAR")
                    insertTag(CalDAV.CompFilter) {
                        attribute(null, COMP_FILTER_NAME, component)
                        if (start != null || end != null) {
                            insertTag(CalDAV.TimeRange) {
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

        var result: List<Property>? = null
        followRedirects({
            httpClient.prepareRequest(location) {
                method = HttpMethod.parse("REPORT")

                header(HttpHeaders.Depth, "1")

                contentType(MIME_XML_UTF8)
                setBody(writer.toString())
            }
        }) { response ->
            result = processMultiStatus(response, callback)
        }
        return result ?: emptyList()
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
    suspend fun multiget(
        urls: List<Url>,
        contentType: String? = null,
        version: String? = null,
        callback: MultiResponseCallback
    ): List<Property> {
        /* <!ELEMENT calendar-multiget ((DAV:allprop |
                                        DAV:propname |
                                        DAV:prop)?, DAV:href+)>
        */
        val serializer = XmlUtils.newSerializer()
        val writer = StringWriter()
        serializer.setOutput(writer)
        serializer.startDocument("UTF-8", null)
        serializer.setPrefix("", WebDAV.NS_WEBDAV)
        serializer.setPrefix("CAL", CalDAV.NS_CALDAV)
        serializer.insertTag(CalDAV.CalendarMultiget) {
            insertTag(WebDAV.Prop) {
                insertTag(WebDAV.GetContentType)     // to determine the character set
                insertTag(WebDAV.GetETag)
                insertTag(CalDAV.ScheduleTag)
                insertTag(CalDAV.CalendarData) {
                    if (contentType != null)
                        attribute(null, CalendarData.CONTENT_TYPE, contentType)
                    if (version != null)
                        attribute(null, CalendarData.VERSION, version)
                }
            }
            for (url in urls)
                insertTag(WebDAV.Href) {
                    serializer.text(url.encodedPath)
                }
        }
        serializer.endDocument()

        var result: List<Property>? = null
        followRedirects({
            httpClient.prepareRequest(location) {
                method = HttpMethod.parse("REPORT")

                contentType(MIME_XML_UTF8)
                setBody(writer.toString())
            }
        }) { response ->
            result = processMultiStatus(response, callback)
        }
        return result ?: emptyList()
    }


    companion object {

        val MIME_ICALENDAR = ContentType.parse("text/calendar")
        val MIME_ICALENDAR_UTF8 = ContentType.parse("text/calendar;charset=utf-8")

        const val COMP_FILTER_NAME = "name"
        const val TIME_RANGE_START = "start"
        const val TIME_RANGE_END = "end"

        private val timeFormatUTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssVV", Locale.US)

    }

}