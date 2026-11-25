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
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.all
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

/**
 * One item emitted while parsing a WebDAV `<multistatus>` response.
 */
sealed interface MultiStatusItem {

    /**
     * A property found directly under `<multistatus>`, outside any `<response>` element,
     * like `sync-token` (see RFC 6578 6.4 DAV:multistatus XML Element).
     */
    data class ExtraProperty(
        val property: Property
    ) : MultiStatusItem

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

}

/**
 * Filters a [Flow] of [MultiStatusItem]s down to the [Response]s it contains, i.e.
 * the result contains only the parsed `<response>` elements of the `<multistatus>`.
 */
fun Flow<MultiStatusItem>.responses(): Flow<Response> =
    filterIsInstance<MultiStatusItem.Response>().map { it.response }

/**
 * Filters a [Flow] of [MultiStatusItem]s down to the [MultiStatusItem.Response]s,
 * i.e. the result contains only the parsed `<response>` elements of the `<multistatus>`
 * together with their relation.
 */
fun Flow<MultiStatusItem>.responsesWithRelation(): Flow<MultiStatusItem.Response> =
    filterIsInstance<MultiStatusItem.Response>()

/**
 * Filters a [Flow] of [MultiStatusItem]s down to the first [Response] whose relation is
 * [Response.HrefRelation.SELF], i.e. the result contains only the first parsed
 * `<response>` element (in the `<multistatus>` stream) that contains a
 * `<href>` that points to the requested resource.
 */
suspend fun Flow<MultiStatusItem>.selfResponse(): Response? =
    filterIsInstance<MultiStatusItem.Response>()
        .firstOrNull { it.relation == Response.HrefRelation.SELF }
        ?.response

/**
 * Collects the [Flow] and returns whether all requests it contains were successful, i.e.
 * whether all responses (and all their propstats) have a 2xx status; an empty flow is
 * considered successful.
 *
 * Intended for single-resource flows like PROPPATCH; not suitable for PROPFIND/REPORT,
 * where 404 propstats are normal (property not found).
 *
 * @return *true* if the flow is empty or contains only 2xx responses with only 2xx propstat elements;
 * *false* if the flow contains at least one non-2xx response or one non-2xx propstat element
 */
suspend fun Flow<MultiStatusItem>.allSuccessful(): Boolean =
    responses().all { response ->
        response.isSuccess() && response.propstat.all { it.status.isSuccess() }
    }
