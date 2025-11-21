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

/**
 * WebDAV XML namespace and elements (as defined in RFC 4918)
 */
object WebDAV {

    const val NAMESPACE = "DAV:"


    // WebDAV XML elements (see Section 14 of RFC 4918)

    val Href = Property.Name(NAMESPACE, "href")
    val Location = Property.Name(NAMESPACE, "location")
    val MultiStatus = Property.Name(NAMESPACE, "multistatus")
    val Prop = Property.Name(NAMESPACE, "prop")
    val PropertyUpdate = Property.Name(NAMESPACE, "propertyupdate")
    val PropFind = Property.Name(NAMESPACE, "propfind")
    val PropStat = Property.Name(WebDAV.NAMESPACE, "propstat")
    val Remove = Property.Name(NAMESPACE, "remove")
    val Response = Property.Name(NAMESPACE, "response")
    val Set = Property.Name(NAMESPACE, "set")
    val Status = Property.Name(NAMESPACE, "status")


    // Collection Synchronization XML elements (see Section 6 of RFC 6578)

    val SyncCollection = Property.Name(NAMESPACE, "sync-collection")
    val SyncLevel = Property.Name(WebDAV.NAMESPACE, "sync-level")
    val Limit = Property.Name(WebDAV.NAMESPACE, "limit")
    val NResults = Property.Name(WebDAV.NAMESPACE, "nresults")

}