/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm

import at.bitfire.dav4jvm.XmlUtils.insertTag
import at.bitfire.dav4jvm.exception.DavException
import at.bitfire.dav4jvm.exception.HttpException
import at.bitfire.dav4jvm.property.CalendarData
import at.bitfire.dav4jvm.property.GetContentType
import at.bitfire.dav4jvm.property.GetETag
import at.bitfire.dav4jvm.property.ScheduleTag
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.logging.Logger

class DavCalendar @JvmOverloads constructor(
        httpClient: OkHttpClient,
        location: HttpUrl,
        log: Logger = Dav4jvm.log
): DavCollection(httpClient, location, log) {

    companion object {
        val MIME_ICALENDAR = "text/calendar".toMediaType()
        val MIME_ICALENDAR_UTF8 = "text/calendar;charset=utf-8".toMediaType()

        val CALENDAR_QUERY = Property.Name(XmlUtils.NS_CALDAV, "calendar-query")
        val CALENDAR_MULTIGET = Property.Name(XmlUtils.NS_CALDAV, "calendar-multiget")

        val FILTER = Property.Name(XmlUtils.NS_CALDAV, "filter")
        val COMP_FILTER = Property.Name(XmlUtils.NS_CALDAV, "comp-filter")
        const val COMP_FILTER_NAME = "name"
        val TIME_RANGE = Property.Name(XmlUtils.NS_CALDAV, "time-range")
        const val TIME_RANGE_START = "start"
        const val TIME_RANGE_END = "end"

        private val timeFormatUTC = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.ROOT)
        init {
            timeFormatUTC.timeZone = TimeZone.getTimeZone("Etc/UTC")
        }
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
     * @throws IOException on I/O error
     * @throws HttpException on HTTP error
     * @throws DavException on WebDAV error
     */
    fun calendarQuery(component: String, start: Date?, end: Date?, callback: DavResponseCallback): List<Property> {
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
        serializer.setPrefix("", XmlUtils.NS_WEBDAV)
        serializer.setPrefix("CAL", XmlUtils.NS_CALDAV)
        serializer.insertTag(CALENDAR_QUERY) {
            insertTag(PROP) {
                insertTag(GetETag.NAME)
            }
            insertTag(FILTER) {
                insertTag(COMP_FILTER) {
                    attribute(null, COMP_FILTER_NAME, "VCALENDAR")
                    insertTag(COMP_FILTER) {
                        attribute(null, COMP_FILTER_NAME, component)
                        if (start != null || end != null) {
                            insertTag(TIME_RANGE) {
                                if (start != null)
                                    attribute(null, TIME_RANGE_START, timeFormatUTC.format(start))
                                if (end != null)
                                    attribute(null, TIME_RANGE_END, timeFormatUTC.format(end))
                            }
                        }
                    }
                }
            }
        }
        serializer.endDocument()

        followRedirects {
            httpClient.newCall(Request.Builder()
                    .url(location)
                    .method("REPORT", writer.toString().toRequestBody(MIME_XML))
                    .header("Depth", "1")
                    .build()).execute()
        }.use {
            return processMultiStatus(it, callback)
        }
    }

    /**
     * Sends a calendar-multiget REPORT to the resource. Received responses are sent
     * to the callback, whether they are successful (2xx) or not.
     *
     * @param urls     list of iCalendar URLs to be requested
     * @param callback called for every WebDAV response XML element in the result
     *
     * @return list of properties which have been received in the Multi-Status response, but
     * are not part of response XML elements
     *
     * @throws IOException on I/O error
     * @throws HttpException on HTTP error
     * @throws DavException on WebDAV error
     */
    fun multiget(urls: List<HttpUrl>, callback: DavResponseCallback): List<Property> {
        /* <!ELEMENT calendar-multiget ((DAV:allprop |
                                        DAV:propname |
                                        DAV:prop)?, DAV:href+)>
        */
        val serializer = XmlUtils.newSerializer()
        val writer = StringWriter()
        serializer.setOutput(writer)
        serializer.startDocument("UTF-8", null)
        serializer.setPrefix("", XmlUtils.NS_WEBDAV)
        serializer.setPrefix("CAL", XmlUtils.NS_CALDAV)
        serializer.insertTag(CALENDAR_MULTIGET) {
            insertTag(PROP) {
                insertTag(GetContentType.NAME)     // to determine the character set
                insertTag(GetETag.NAME)
                insertTag(ScheduleTag.NAME)
                insertTag(CalendarData.NAME)
            }
            for (url in urls)
                insertTag(HREF) {
                    serializer.text(url.encodedPath)
                }
        }
        serializer.endDocument()

        followRedirects {
            httpClient.newCall(Request.Builder()
                    .url(location)
                    .method("REPORT", writer.toString().toRequestBody(MIME_XML))
                    .build()).execute()
        }.use {
            return processMultiStatus(it, callback)
        }
    }

}
