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
import at.bitfire.dav4jvm.QuotedStringUtils
import at.bitfire.dav4jvm.XmlReader
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import okhttp3.Response
import org.xmlpull.v1.XmlPullParser

/**
 * The GetETag property.
 *
 * Can also be used to parse ETags from HTTP responses – just pass the raw ETag
 * header value to the constructor and then use [eTag] and [weak].
 */
data class GetETag(
    val rawETag: String?
): Property {

    companion object {

        fun fromHttpResponse(response: HttpResponse) =
            response.headers[HttpHeaders.ETag]?.let { GetETag(it) }

        fun fromResponse(response: Response) =
            response.header(HttpHeaders.ETag)?.let { GetETag(it) }
    }

    /**
     * The parsed ETag value, excluding the weakness indicator and the quotes.
     */
    val eTag: String?

    /**
     * Whether the ETag is weak.
     */
    var weak: Boolean

    init {
        /* entity-tag = [ weak ] opaque-tag
           weak       = "W/"
           opaque-tag = quoted-string
        */

        if (rawETag != null) {
            val tag: String?
            // remove trailing "W/"
            if (rawETag.startsWith("W/")) {
                // entity tag is weak
                tag = rawETag.substring(2)
                weak = true
            } else {
                tag = rawETag
                weak = false
            }
            eTag = QuotedStringUtils.decodeQuotedString(tag)
        } else {
            eTag = null
            weak = false
        }
    }

    override fun equals(other: Any?): Boolean {
        if (other !is GetETag)
            return false
        return eTag == other.eTag && weak == other.weak
    }

    override fun hashCode(): Int {
        return eTag.hashCode() xor weak.hashCode()
    }


    object Factory: PropertyFactory {

        override fun getName() = WebDAV.GetETag

        override fun create(parser: XmlPullParser): GetETag =
            GetETag(XmlReader(parser).readText())

    }

}
