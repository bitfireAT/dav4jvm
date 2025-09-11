/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

package at.bitfire.dav4jvm.okhttp.property

import at.bitfire.dav4jvm.okhttp.property.webdav.Owner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnerTest: PropertyTest() {

    @Test
    fun testOwner_Empty() {
        val results = parseProperty("<owner></owner>")
        assertTrue(results.isEmpty())
    }

    @Test
    fun testOwner_PlainText() {
        val results = parseProperty("<owner xmlns=\"DAV:\">https://example.com</owner>")
        val owner = results.first() as Owner
        assertNull(owner.href)
    }

    @Test
    fun testOwner_PlainTextAndHref() {
        val results = parseProperty("<owner xmlns=\"DAV:\">Principal Name. <href>mailto:owner@example.com</href> (test)</owner>")
        val owner = results.first() as Owner
        assertEquals("mailto:owner@example.com", owner.href)
    }

    @Test
    fun testOwner_Href() {
        val results = parseProperty("<owner xmlns=\"DAV:\"><href>https://example.com</href></owner>")
        val owner = results.first() as Owner
        assertEquals("https://example.com", owner.href)
    }

    @Test
    fun testOwner_TwoHrefs() {
        val results = parseProperty("<owner xmlns=\"DAV:\">" +
                "<href>https://example.com/owner1</href>" +
                "<href>https://example.com/owner2</href>" +
                "</owner>")
        val owner = results.first() as Owner
        assertEquals("https://example.com/owner1", owner.href)
    }

    @Test
    fun testOwner_WithoutHref() {
        val results = parseProperty("<owner>invalid</owner>")
        assertTrue(results.isEmpty())
    }

}