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
import at.bitfire.dav4jvm.XmlUtils
import at.bitfire.dav4jvm.property.webdav.WebDAV
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.StringReader

class ErrorTest {

    companion object {
        private fun parseError(xml: String): List<Error> {
            val parser = XmlUtils.newPullParser()
            parser.setInput(StringReader("<error xmlns='DAV:'>$xml</error>"))
            parser.nextTag()   // <error>
            return Error.parseError(parser)
        }
    }


    @Test
    fun testParseError_empty() {
        val errors = parseError("")
        assertEquals(0, errors.size)
    }

    @Test
    fun testParseError_singleChild() {
        val errors = parseError("<valid-sync-token/>")
        assertEquals(1, errors.size)
        assertTrue(errors.contains(Error(WebDAV.ValidSyncToken)))
    }

    @Test
    fun testParseError_multipleChildren() {
        val errors = parseError("<valid-sync-token/><lock-token-matches-request-uri/>")
        assertEquals(2, errors.size)
        assertTrue(errors.contains(Error(WebDAV.ValidSyncToken)))
        assertTrue(errors.contains(Error(Property.Name("DAV:", "lock-token-matches-request-uri"))))
    }

    @Test
    fun testParseError_duplicatesDeduped() {
        val errors = parseError("<valid-sync-token/><valid-sync-token/>")
        assertEquals(1, errors.size)
    }

    @Test
    fun testParseError_customNamespace() {
        val errors = parseError("<my-error xmlns='http://example.com/'/>")
        assertEquals(1, errors.size)
        assertTrue(errors.contains(Error(Property.Name("http://example.com/", "my-error"))))
    }


    @Test
    fun testEquals() {
        val errors = listOf(Error(Property.Name("DAV:", "valid-sync-token")))
        assertTrue(errors.contains(Error(WebDAV.ValidSyncToken)))
    }

    @Test
    fun testNotEquals() {
        assertFalse(Error(WebDAV.ValidSyncToken) == Error(Property.Name("DAV:", "other")))
    }

    @Test
    fun testHashCode() {
        assertEquals(
            Error(WebDAV.ValidSyncToken).hashCode(),
            Error(Property.Name("DAV:", "valid-sync-token")).hashCode()
        )
    }

}
