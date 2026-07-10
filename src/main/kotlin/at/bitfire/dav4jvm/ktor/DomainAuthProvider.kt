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

import at.bitfire.dav4jvm.ktor.UrlUtils.hostToDomain
import io.ktor.client.plugins.auth.AuthProvider
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.http.auth.HttpAuthHeader

/**
 * An [AuthProvider] wrapper that can limit authentication to a given domain.
 *
 * @param firstLevelDomain Credentials will only be set on requests to this first-level domain and its subdomains.
 *   If `null`, setting credentials is not restricted by the request's domain.
 * @param authProviderDelegate The [AuthProvider] to delegate to.
 */
class DomainAuthProvider(
    private val firstLevelDomain: String?,
    private val authProviderDelegate: AuthProvider
) : AuthProvider by authProviderDelegate {

    override suspend fun addRequestHeaders(request: HttpRequestBuilder, authHeader: HttpAuthHeader?) {
        if (isDomainMatch(request)) {
            authProviderDelegate.addRequestHeaders(request, authHeader)
        }
    }

    override fun sendWithoutRequest(request: HttpRequestBuilder): Boolean {
        return if (isDomainMatch(request)) {
            authProviderDelegate.sendWithoutRequest(request)
        } else {
            false
        }
    }

    private fun isDomainMatch(request: HttpRequestBuilder): Boolean {
        return firstLevelDomain == null || firstLevelDomain.equals(hostToDomain(request.url.host), ignoreCase = true)
    }
}
