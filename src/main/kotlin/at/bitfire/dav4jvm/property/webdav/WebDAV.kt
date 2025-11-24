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

    const val NS_WEBDAV = "DAV:"


    // WebDAV XML elements/properties (see Section 14 and 15 of RFC 4918)

    val Collection = Property.Name(NS_WEBDAV, "collection")
    val CreationDate = Property.Name(NS_WEBDAV, "creationdate")
    val Depth = Property.Name(NS_WEBDAV, "depth")
    val DisplayName = Property.Name(NS_WEBDAV, "displayname")
    val Error = Property.Name(NS_WEBDAV, "error")
    val GetContentLength = Property.Name(NS_WEBDAV, "getcontentlength")
    val GetContentType = Property.Name(NS_WEBDAV, "getcontenttype")
    val GetETag = Property.Name(NS_WEBDAV, "getetag")
    val GetLastModified = Property.Name(NS_WEBDAV, "getlastmodified")
    val Href = Property.Name(NS_WEBDAV, "href")
    val Location = Property.Name(NS_WEBDAV, "location")
    val MultiStatus = Property.Name(NS_WEBDAV, "multistatus")
    val Owner = Property.Name(NS_WEBDAV, "owner")
    val Principal = Property.Name(NS_WEBDAV, "principal")
    val Prop = Property.Name(NS_WEBDAV, "prop")
    val PropertyUpdate = Property.Name(NS_WEBDAV, "propertyupdate")
    val PropFind = Property.Name(NS_WEBDAV, "propfind")
    val PropStat = Property.Name(NS_WEBDAV, "propstat")
    val Remove = Property.Name(NS_WEBDAV, "remove")
    val ResourceType = Property.Name(NS_WEBDAV, "resourcetype")
    val Response = Property.Name(NS_WEBDAV, "response")
    val Set = Property.Name(NS_WEBDAV, "set")
    val Status = Property.Name(NS_WEBDAV, "status")


    // Versioning Extensions to WebDAV (RFC 3253)

    val Report = Property.Name(NS_WEBDAV, "report")
    val SupportedReportSet = Property.Name(NS_WEBDAV, "supported-report-set")
    val SupportedReport = Property.Name(NS_WEBDAV, "supported-report")


    // WebDAV ACL (RFC 3744)

    val All = Property.Name(NS_WEBDAV, "all")
    val Bind = Property.Name(NS_WEBDAV, "bind")
    val CurrentUserPrivilegeSet = Property.Name(NS_WEBDAV, "current-user-privilege-set")
    val GroupMembership = Property.Name(NS_WEBDAV, "group-membership")
    val NeedPrivileges = Property.Name(NS_WEBDAV, "need-privileges")
    val Privilege = Property.Name(NS_WEBDAV, "privilege")
    val Read = Property.Name(NS_WEBDAV, "read")
    val Unbind = Property.Name(NS_WEBDAV, "unbind")
    val Write = Property.Name(NS_WEBDAV, "write")
    val WriteContent = Property.Name(NS_WEBDAV, "write-content")
    val WriteProperties = Property.Name(NS_WEBDAV, "write-properties")


    // Quota and Size Properties for WebDAV (RFC 4331)

    val QuotaAvailableBytes = Property.Name(NS_WEBDAV, "quota-available-bytes")
    val QuotaUsedBytes = Property.Name(NS_WEBDAV, "quota-used-bytes")


    // WebDAV Current Principal Extension (RFC 5397)

    val CurrentUserPrincipal = Property.Name(NS_WEBDAV, "current-user-principal")


    // Using POST to Add Members (RFC 5995)

    val AddMember = Property.Name(NS_WEBDAV, "add-member")


    // Collection Synchronization (RFC 6578)

    val Limit = Property.Name(NS_WEBDAV, "limit")
    val NResults = Property.Name(NS_WEBDAV, "nresults")
    val SyncCollection = Property.Name(NS_WEBDAV, "sync-collection")
    val SyncLevel = Property.Name(NS_WEBDAV, "sync-level")
    val SyncToken = Property.Name(NS_WEBDAV, "sync-token")
    val ValidSyncToken = Property.Name(NS_WEBDAV, "valid-sync-token")

}