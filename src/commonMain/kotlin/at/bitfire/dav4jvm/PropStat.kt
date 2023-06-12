/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.dav4jvm

import at.bitfire.dav4jvm.Response.Companion.STATUS
import at.bitfire.dav4jvm.XmlUtils.nextText
import io.ktor.http.*
import nl.adaptivity.xmlutil.EventType
import nl.adaptivity.xmlutil.QName
import nl.adaptivity.xmlutil.XmlReader
import kotlin.jvm.JvmField

/**
 * Represents a WebDAV propstat XML element.
 *
 *     <!ELEMENT propstat (prop, status, error?, responsedescription?) >
 */
data class PropStat(
        val properties: List<Property>,
        val status: HttpStatusCode,
        val error: List<Error>? = null
) {

    companion object {

        @JvmField
        val NAME = QName(XmlUtils.NS_WEBDAV, "propstat")

        private val ASSUMING_OK = HttpStatusCode(200, "Assuming OK")
        private val INVALID_STATUS = HttpStatusCode( 500, "Invalid status line")

        fun parse(parser: XmlReader): PropStat {
            val depth = parser.depth

            var status: HttpStatusCode? = null
            val prop = mutableListOf<Property>()

            var eventType = parser.eventType
            while (!(eventType == EventType.END_ELEMENT && parser.depth == depth)) {
                if (eventType == EventType.START_ELEMENT && parser.depth == depth + 1)
                    when (parser.name) {
                        DavResource.PROP ->
                            prop.addAll(Property.parse(parser))
                        STATUS ->
                            status = try {
                                StatusLine.parse(parser.nextText()).status
                            } catch (e: IllegalStateException) {
                                // invalid status line, treat as 500 Internal Server Error
                                INVALID_STATUS
                            }
                    }
                eventType = parser.next()
            }

            return PropStat(prop, status ?: ASSUMING_OK)
        }

    }


    fun isSuccess() = status.isSuccess()
}