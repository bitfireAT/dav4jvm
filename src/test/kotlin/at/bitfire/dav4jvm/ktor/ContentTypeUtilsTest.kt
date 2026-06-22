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

import io.ktor.http.ContentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentTypeUtilsTest {

    @Test
    fun `isText with text plain`() =
        assertTrue(ContentType.Text.Plain.isText())

    @Test
    fun `isText with text xml`() =
        assertTrue(ContentType.Text.Xml.isText())

    @Test
    fun `isText with application xml`() =
        assertTrue(ContentType.Application.Xml.isText())

    @Test
    fun `isText with application json`() =
        assertFalse(ContentType.Application.Json.isText())


    @Test
    fun `isXml with application xml`() =
        assertTrue(ContentType.Application.Xml.isXml())

    @Test
    fun `isXml with text xml`() =
        assertTrue(ContentType.Text.Xml.isXml())

    @Test
    fun `isXml with text plain`() =
        assertFalse(ContentType.Text.Plain.isXml())


    @Test
    fun `toContentTypeOrNull with correct MIME type`() {
        assertEquals(
            ContentType.parse("text/x-example"),
            "text/x-example".toContentTypeOrNull()
        )
    }

    @Test
    fun `toContentTypeOrNull with invalid MIME type`() {
        assertNull("INVALID".toContentTypeOrNull())
    }

    @Test
    fun `toContentTypeOrNull with null`() =
        assertNull(null.toContentTypeOrNull())

}
