/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm

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
import org.xmlpull.v1.XmlSerializer
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

        private val timeFormatUTC = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US)
        init {
            timeFormatUTC.timeZone = TimeZone.getTimeZone("UTC")
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
        serializer.startTag(XmlUtils.NS_CALDAV, "calendar-query")
            serializer.startTag(XmlUtils.NS_WEBDAV, "prop")
                serializer.startTag(XmlUtils.NS_WEBDAV, "getetag")
                serializer.endTag(XmlUtils.NS_WEBDAV, "getetag")
            serializer.endTag(XmlUtils.NS_WEBDAV, "prop")
            serializer.startTag(XmlUtils.NS_CALDAV, "filter")
                serializer.startTag(XmlUtils.NS_CALDAV, "comp-filter")
                serializer.attribute(null, "name", "VCALENDAR")
                    serializer.startTag(XmlUtils.NS_CALDAV, "comp-filter")
                    serializer.attribute(null, "name", component)
                    if (start != null || end != null) {
                        serializer.startTag(XmlUtils.NS_CALDAV, "time-range")
                        if (start != null)
                            serializer.attribute(null, "start", timeFormatUTC.format(start))
                        if (end != null)
                            serializer.attribute(null, "end", timeFormatUTC.format(end))
                        serializer.endTag(XmlUtils.NS_CALDAV, "time-range")
                    }
                    serializer.endTag(XmlUtils.NS_CALDAV, "comp-filter")
                serializer.endTag(XmlUtils.NS_CALDAV, "comp-filter")
            serializer.endTag(XmlUtils.NS_CALDAV, "filter")
        serializer.endTag(XmlUtils.NS_CALDAV, "calendar-query")
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
        fun XmlSerializer.emptyTag(propertyName: Property.Name) {
            startTag(propertyName.namespace, propertyName.name)
            endTag(propertyName.namespace, propertyName.name)
        }

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
        serializer.startTag(XmlUtils.NS_CALDAV, "calendar-multiget")
            serializer.startTag(XmlUtils.NS_WEBDAV, "prop")
                serializer.emptyTag(GetContentType.NAME)     // to determine the character set
                serializer.emptyTag(GetETag.NAME)
                serializer.emptyTag(ScheduleTag.NAME)
                serializer.emptyTag(CalendarData.NAME)
            serializer.endTag(XmlUtils.NS_WEBDAV, "prop")
            for (url in urls) {
                serializer.startTag(XmlUtils.NS_WEBDAV, "href")
                    serializer.text(url.encodedPath)
                serializer.endTag(XmlUtils.NS_WEBDAV, "href")
            }
        serializer.endTag(XmlUtils.NS_CALDAV, "calendar-multiget")
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
