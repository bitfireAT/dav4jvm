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

import at.bitfire.dav4jvm.property.webdav.SyncToken
import io.ktor.http.Url
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiStatusItemTest {

    private fun response(href: String) =
        Response(
            requestedUrl = Url("http://example.com/dav/"),
            href = Url(href),
            status = null,
            propstat = emptyList()
        )

    private fun item(href: String, relation: Response.HrefRelation) =
        MultiStatusItem.Response(response(href), relation)


    // responses

    @Test
    fun `responses() with only Response items returns all of them in order`() = runTest {
        val self = item("http://example.com/dav/", Response.HrefRelation.SELF)
        val member = item("http://example.com/dav/1.ics", Response.HrefRelation.MEMBER)

        val result = flowOf(self, member).responses().toList()
        assertEquals(listOf(self.response, member.response), result)
    }

    @Test
    fun `responses() drops ExtraProperty items`() = runTest {
        val member = item("http://example.com/dav/1.ics", Response.HrefRelation.MEMBER)
        val extra = MultiStatusItem.ExtraProperty(SyncToken("http://sync/1"))

        val result = flowOf(extra, member, extra).responses().toList()
        assertEquals(listOf(member.response), result)
    }

    @Test
    fun `responses() on a flow without Response items returns an empty flow`() = runTest {
        val extra = MultiStatusItem.ExtraProperty(SyncToken("http://sync/1"))

        val result = flowOf<MultiStatusItem>(extra).responses().toList()
        assertTrue(result.isEmpty())
    }


    // responsesWithRelation

    @Test
    fun `responsesWithRelation() with only Response items returns all of them together with their relation in order`() = runTest {
        val self = item("http://example.com/dav/", Response.HrefRelation.SELF)
        val member = item("http://example.com/dav/1.ics", Response.HrefRelation.MEMBER)

        val result = flowOf(self, member).responsesWithRelation().toList()
        assertEquals(
            listOf(
                self.response to Response.HrefRelation.SELF,
                member.response to Response.HrefRelation.MEMBER
            ),
            result
        )
    }

    @Test
    fun `responsesWithRelation() drops ExtraProperty items`() = runTest {
        val member = item("http://example.com/dav/1.ics", Response.HrefRelation.MEMBER)
        val extra = MultiStatusItem.ExtraProperty(SyncToken("http://sync/1"))

        val result = flowOf(extra, member, extra).responsesWithRelation().toList()
        assertEquals(listOf(member.response to Response.HrefRelation.MEMBER), result)
    }

    @Test
    fun `responsesWithRelation() on a flow without Response items returns an empty flow`() = runTest {
        val extra = MultiStatusItem.ExtraProperty(SyncToken("http://sync/1"))

        val result = flowOf<MultiStatusItem>(extra).responsesWithRelation().toList()
        assertTrue(result.isEmpty())
    }


    // selfResponse

    @Test
    fun `selfResponse() returns the SELF response when present`() = runTest {
        val member = item("http://example.com/dav/1.ics", Response.HrefRelation.MEMBER)
        val self = item("http://example.com/dav/", Response.HrefRelation.SELF)

        // SELF is not the first item — selfResponse() must not just take the first Response
        val result = flowOf(member, self).selfResponse()
        assertEquals(self.response, result)
    }

    @Test
    fun `selfResponse() returns null when no SELF item is present`() = runTest {
        val member = item("http://example.com/dav/1.ics", Response.HrefRelation.MEMBER)
        val extra = MultiStatusItem.ExtraProperty(SyncToken("http://sync/1"))

        val result = flowOf(extra, member).selfResponse()
        assertNull(result)
    }

}
