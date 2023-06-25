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
import nl.adaptivity.xmlutil.QName
import nl.adaptivity.xmlutil.XmlReader
import okhttp3.Protocol
import okhttp3.internal.http.StatusLine
import okio.ProtocolException
import java.util.*

/**
 * Represents a WebDAV propstat XML element.
 *
 *     <!ELEMENT propstat (prop, status, error?, responsedescription?) >
 */
data class PropStat(
    val properties: List<Property>,
    val status: StatusLine,
    val error: List<Error>? = null
) {

    companion object {

        @JvmField
        val NAME = QName(XmlUtils.NS_WEBDAV, "propstat")

        private val ASSUMING_OK = StatusLine(Protocol.HTTP_1_1, 200, "Assuming OK")
        private val INVALID_STATUS = StatusLine(Protocol.HTTP_1_1, 500, "Invalid status line")

        fun parse(parser: XmlReader): PropStat {
            var status: StatusLine? = null
            val prop = LinkedList<Property>()

            XmlUtils.processTag(parser) {
                when (parser.name) {
                    DavResource.PROP ->
                        prop.addAll(Property.parse(parser))

                    STATUS ->
                        status = try {
                            StatusLine.parse(parser.nextText())
                        } catch (e: ProtocolException) {
                            // invalid status line, treat as 500 Internal Server Error
                            INVALID_STATUS
                        }
                }
            }

            return PropStat(prop, status ?: ASSUMING_OK)
        }
    }

    fun isSuccess() = status.code / 100 == 2
}
