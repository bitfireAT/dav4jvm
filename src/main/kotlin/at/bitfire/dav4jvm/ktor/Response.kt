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

import at.bitfire.dav4jvm.Error
import at.bitfire.dav4jvm.Property
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.isSuccess

/**
 * Represents a WebDAV response XML Element.
 *
 *     <!ELEMENT response (href, ((href*, status)|(propstat+)),
 *                         error?, responsedescription? , location?) >
 */
@Suppress("unused")
data class Response(
    /**
     * URL of the requested resource. For instance, if `this` is a result
     * of a PROPFIND request, the `requestedUrl` would be the URL where the
     * PROPFIND request has been sent to (usually the collection URL).
     */
    val requestedUrl: Url,

    /**
     * URL of this response (`href` element)
     */
    val href: Url,

    /**
     * status of this response (`status` XML element)
     */
    val status: HttpStatusCode?,

    /**
     * property/status elements (`propstat` XML elements)
     */
    val propstat: List<PropStat>,

    /**
     * list of precondition/postcondition elements (`error` XML elements)
     */
    val error: List<Error>? = null,

    /**
     * new location of this response (`location` XML element), used for redirects
     */
    val newLocation: Url? = null
) {

    enum class HrefRelation {
        SELF, MEMBER, OTHER
    }

    /**
     * All properties from propstat elements with empty status or status code 2xx.
     */
    val properties: List<Property> by lazy {
        if (isSuccess())
            propstat.filter { it.status.isSuccess() }.map { it.properties }.flatten()
        else
            emptyList()
    }

    /**
     * Convenience method to get a certain property with empty status or status code 2xx
     * from the current response.
     */
    operator fun<T: Property> get(clazz: Class<T>) =
            properties.filterIsInstance(clazz).firstOrNull()

    /**
     * Returns whether the request was successful.
     *
     * @return true: no status XML element or status code 2xx; false: otherwise
     */
    fun isSuccess() = status == null || status.isSuccess()

    /**
     * Returns the name (last path segment) of the resource.
     */
    fun hrefName() = KtorHttpUtils.fileName(href)

}