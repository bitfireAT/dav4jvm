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

import io.ktor.client.statement.HttpResponse

/**
 * Callback for the OPTIONS request.
 */
fun interface CapabilitiesCallback {
    suspend fun onCapabilities(davCapabilities: Set<String>, response: HttpResponse)
}

/**
 * Callback for 207 Multi-Status responses.
 */
fun interface MultiResponseCallback {
    /**
     * Called for every `<response>` element in the `<multistatus>` body. For instance,
     * in response to a `PROPFIND` request, this callback will be called once for every found
     * member resource.
     *
     * Known collections have [response] `href` with trailing slash, see [Response.parse] for details.
     *
     * @param response   the parsed response (including URL)
     * @param relation   relation of the response to the called resource
     */
    suspend fun onResponse(response: Response, relation: Response.HrefRelation)
}

/**
 * Callback for HTTP responses.
 */
fun interface ResponseCallback {
    /**
     * Called for a HTTP response. Typically this is only called for successful/redirect
     * responses because HTTP errors throw an exception before this callback is called.
     *
     * @param response      scoped response that can be used to access the body
     *                      in a streaming way (**body won't be accessible anymore when
     *                      callback returns!**)
     */
    suspend fun onResponse(response: HttpResponse)
}