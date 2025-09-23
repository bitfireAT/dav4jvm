/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

package at.bitfire.dav4jvm.okhttp

import okhttp3.HttpUrl
import okhttp3.Response

object OkHttpUtils {

    /**
     * Gets the resource name (the last segment of the path) from an URL.
     * Empty if the resource is the base directory.
     *
     * * `dir` for `https://example.com/dir/`
     * * `file` for `https://example.com/file`
     * * `` for `https://example.com` or  `https://example.com/`
     *
     * @return resource name
     */
    fun fileName(url: HttpUrl): String {
        val pathSegments = url.pathSegments.dropLastWhile { it == "" }
        return pathSegments.lastOrNull() ?: ""
    }

    /**
     * Gets all values of a header that is defined as a list [RFC 9110 5.6.1],
     * regardless of they're sent as one line or as multiple lines.
     *
     * For instance, regardless of whether a server sends:
     *
     * ```
     * DAV: 1
     * DAV: 2
     * ```
     *
     * or
     *
     * ```
     * DAV: 1, 2
     * ```
     *
     * this method would return `arrayOf("1","2")` for the `DAV` header.
     *
     * @param response  the HTTP response to evaluate
     * @param name      header name (for instance: `DAV`)
     *
     * @return all values for the given header name
     */
    fun listHeader(response: Response, name: String): Array<String> {
        val value = response.headers(name).joinToString(",")
        return value.split(',').filter { it.isNotEmpty() }.toTypedArray()
    }

}