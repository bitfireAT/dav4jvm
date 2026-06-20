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

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import io.ktor.http.withCharset
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DavAddressBookTest {

    private val sampleUrl = Url("http://127.0.0.1/dav/")

    private fun minimalMultiStatus() = MockEngine { _ ->
        respond(
            "<?xml version=\"1.0\" encoding=\"utf-8\"?><multistatus xmlns=\"DAV:\"/>",
            HttpStatusCode.MultiStatus,
            headersOf(HttpHeaders.ContentType, ContentType.Text.Xml.withCharset(Charsets.UTF_8).toString())
        )
    }

    private fun davAddressBook(engine: MockEngine) = DavAddressBook(HttpClient(engine), sampleUrl)

    private suspend fun requestBody(engine: MockEngine) =
        engine.requestHistory.last().body.toByteArray().toString(Charsets.UTF_8)


    @Test
    fun `addressbookQuery sends REPORT with Depth 1`() = runTest {
        val engine = minimalMultiStatus()
        davAddressBook(engine).addressbookQuery { _, _ -> }
        with(engine.requestHistory.last()) {
            assertEquals(HttpMethod.parse("REPORT"), method)
            assertEquals("1", headers[HttpHeaders.Depth])
        }
    }

    @Test
    fun `addressbookQuery request body contains query and filter`() = runTest {
        val engine = minimalMultiStatus()
        davAddressBook(engine).addressbookQuery { _, _ -> }
        val body = requestBody(engine)
        assertTrue(body.contains("CARD:addressbook-query"))
        assertTrue(body.contains("<getetag />"))
        assertTrue(body.contains("<CARD:filter />"))
    }

    @Test
    fun `multiget sends REPORT with Depth 0`() = runTest {
        val engine = minimalMultiStatus()
        davAddressBook(engine).multiget(listOf(sampleUrl)) { _, _ -> }
        with(engine.requestHistory.last()) {
            assertEquals(HttpMethod.parse("REPORT"), method)
            assertEquals("0", headers[HttpHeaders.Depth])
        }
    }

    @Test
    fun `multiget request body contains hrefs and address-data`() = runTest {
        val engine = minimalMultiStatus()
        val url1 = Url("http://127.0.0.1/dav/contact1.vcf")
        val url2 = Url("http://127.0.0.1/dav/contact2.vcf")
        davAddressBook(engine).multiget(listOf(url1, url2)) { _, _ -> }
        val body = requestBody(engine)
        assertTrue(body.contains("CARD:addressbook-multiget"))
        assertTrue(body.contains("<href>/dav/contact1.vcf</href>"))
        assertTrue(body.contains("<href>/dav/contact2.vcf</href>"))
        assertTrue(body.contains("<getcontenttype />"))
        assertTrue(body.contains("<getetag />"))
        assertTrue(body.contains("<CARD:address-data />"))
        assertFalse(body.contains("content-type="))
    }

    @Test
    fun `multiget with contentType adds attributes to address-data`() = runTest {
        val engine = minimalMultiStatus()
        davAddressBook(engine).multiget(listOf(sampleUrl), contentType = "text/vcard", version = "4.0") { _, _ -> }
        val body = requestBody(engine)
        assertTrue(body.contains("content-type=\"text/vcard\""))
        assertTrue(body.contains("version=\"4.0\""))
    }

}
