/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm.property

import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.PropertyFactory
import at.bitfire.dav4jvm.XmlUtils
import org.xmlpull.v1.XmlPullParser

class SupportedReportSet: Property {

    companion object {
        @JvmField
        val NAME = Property.Name(XmlUtils.NS_WEBDAV, "supported-report-set")

        const val SYNC_COLLECTION = "DAV:sync-collection"    // collection synchronization (RFC 6578)
    }

    val reports = mutableSetOf<String>()

    override fun toString() = "[${reports.joinToString(", ")}]"


    class Factory: PropertyFactory {

        override fun getName() = NAME

        override fun create(parser: XmlPullParser): SupportedReportSet? {
            /* <!ELEMENT supported-report-set (supported-report*)>
               <!ELEMENT supported-report report>
               <!ELEMENT report ANY>
            */

            val supported = SupportedReportSet()
            XmlUtils.processTag(parser, XmlUtils.NS_WEBDAV, "supported-report") {
                XmlUtils.processTag(parser, XmlUtils.NS_WEBDAV, "report") {
                    parser.nextTag()
                    if (parser.eventType == XmlPullParser.TEXT)
                        supported.reports += parser.text
                    else if (parser.eventType == XmlPullParser.START_TAG)
                        supported.reports += "${parser.namespace}${parser.name}"
                }
            }
            return supported
        }

    }

}
