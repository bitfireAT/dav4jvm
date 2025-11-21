/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

package at.bitfire.dav4jvm.property.webdav

import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.PropertyFactory
import at.bitfire.dav4jvm.XmlReader
import org.xmlpull.v1.XmlPullParser

data class SupportedReportSet(
    val reports: Set<String> = emptySet()
): Property {

    companion object {

        @JvmField
        val NAME = Property.Name(WebDAV.NAMESPACE, "supported-report-set")

        val SUPPORTED_REPORT = Property.Name(WebDAV.NAMESPACE, "supported-report")
        val REPORT = Property.Name(WebDAV.NAMESPACE, "report")

        const val SYNC_COLLECTION = "DAV:sync-collection"    // collection synchronization (RFC 6578)

    }


    object Factory: PropertyFactory {

        override fun getName() = NAME

        override fun create(parser: XmlPullParser): SupportedReportSet {
            /* <!ELEMENT supported-report-set (supported-report*)>
               <!ELEMENT supported-report report>
               <!ELEMENT report ANY>
            */

            val reports = mutableSetOf<String>()
            XmlReader(parser).processTag(SUPPORTED_REPORT) {
                processTag(REPORT) {
                    parser.nextTag()
                    if (parser.eventType == XmlPullParser.TEXT)
                        reports += parser.text
                    else if (parser.eventType == XmlPullParser.START_TAG)
                        reports += "${parser.namespace}${parser.name}"
                }
            }
            return SupportedReportSet(reports)
        }

    }

}
