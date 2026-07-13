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

import at.bitfire.dav4jvm.Property

/**
 * One item emitted while parsing a WebDAV `<multistatus>` response.
 */
sealed interface MultiStatusItem {

    /**
     * One `<response>` element of the Multi-Status body.
     *
     * @param response  the parsed response (including URL)
     * @param relation  relation of the response to the called resource
     */
    data class Response(
        val response: at.bitfire.dav4jvm.ktor.Response,
        val relation: at.bitfire.dav4jvm.ktor.Response.HrefRelation
    ) : MultiStatusItem

    /**
     * A property found directly under `<multistatus>`, outside any `<response>` element,
     * like `sync-token` (see RFC 6578 6.4 DAV:multistatus XML Element).
     */
    data class ExtraProperty(
        val property: Property
    ) : MultiStatusItem

}
