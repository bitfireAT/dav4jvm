/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4android

import at.bitfire.dav4android.exception.DavException
import at.bitfire.dav4android.exception.HttpException
import okhttp3.*
import java.io.IOException
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.logging.Logger

class DavCalendar @JvmOverloads constructor(
        httpClient: OkHttpClient,
        location: HttpUrl,
        log: Logger = Constants.log
): DavCollection(httpClient, location, log) {

    companion object {
        val MIME_ICALENDAR = MediaType.parse("text/calendar")
        val MIME_ICALENDAR_UTF8 = MediaType.parse("text/calendar;charset=utf-8")

        private val timeFormatUTC = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.US)
        init {
            timeFormatUTC.timeZone = TimeZone.getTimeZone("UTC")
        }
    }


    /**
     * Sends a calendar-query REPORT to the resource.
     * @throws IOException on I/O error
     * @throws HttpException on HTTP error
     * @throws DavException on DAV error
     */
    fun calendarQuery(component: String, start: Date?, end: Date?): DavResponse {
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

        val response = httpClient.newCall(Request.Builder()
                .url(location)
                .method("REPORT", RequestBody.create(MIME_XML, writer.toString()))
                .header("Depth", "1")
                .build()).execute()

        checkStatus(response)
        assertMultiStatus(response)

        return processMultiStatus(response.body()?.charStream()!!)
    }

    /**
     * Sends a calendar-multiget REPORT to the resource.
     * @throws IOException on I/O error
     * @throws HttpException on HTTP error
     * @throws DavException on DAV error
     */
    fun multiget(urls: List<HttpUrl>): DavResponse {
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
                serializer.startTag(XmlUtils.NS_WEBDAV, "getcontenttype")      // to determine the character set
                serializer.endTag(XmlUtils.NS_WEBDAV, "getcontenttype")
                serializer.startTag(XmlUtils.NS_WEBDAV, "getetag")
                serializer.endTag(XmlUtils.NS_WEBDAV, "getetag")
                serializer.startTag(XmlUtils.NS_CALDAV, "calendar-data")
                serializer.endTag(XmlUtils.NS_CALDAV, "calendar-data")
            serializer.endTag(XmlUtils.NS_WEBDAV, "prop")
            for (url in urls) {
                serializer.startTag(XmlUtils.NS_WEBDAV, "href")
                    serializer.text(url.encodedPath())
                serializer.endTag(XmlUtils.NS_WEBDAV, "href")
            }
        serializer.endTag(XmlUtils.NS_CALDAV, "calendar-multiget")
        serializer.endDocument()

        val response = httpClient.newCall(Request.Builder()
                .url(location)
                .method("REPORT", RequestBody.create(MIME_XML, writer.toString()))
                .build()).execute()

        checkStatus(response)
        assertMultiStatus(response)

        return processMultiStatus(response.body()?.charStream()!!)
    }

}
