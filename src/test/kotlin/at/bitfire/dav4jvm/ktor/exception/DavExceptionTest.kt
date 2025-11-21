/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

package at.bitfire.dav4jvm.ktor.exception

import at.bitfire.dav4jvm.Error
import at.bitfire.dav4jvm.Property
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

class DavExceptionTest {

    private val sampleUrl = Url("https://127.0.0.1/dav/")

    @Test
    fun fromResponse() = runTest {
        val mockEngine = MockEngine {
            respond(
                status = HttpStatusCode.OK,
                content = "Your Information",
                headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Plain.toString())
            )
        }
        val httpClient = HttpClient(mockEngine)
        val response = httpClient.get(sampleUrl)
        val cause = Exception()
        val result = DavException.fromResponse("Unexpected response", response, cause = cause)

        assertEquals("Unexpected response", result.message)
        assertEquals(cause, result.cause)
        assertEquals(200, result.statusCode)
        assertEquals("GET $sampleUrl", result.requestExcerpt)
        assertEquals("Your Information", result.responseExcerpt)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `is Java-serializable`() {
        val ex = DavException(
            message = "Some Error",
            statusCode = 500,
            requestExcerpt = "Request Body",
            responseExcerpt = "Response Body",
            errors = listOf(
                Error(Property.Name("Serialized", "Name"))
            ),
            cause = FileNotFoundException()
        )

        // serialize (Java-style as in Serializable interface, not Kotlin serialization)
        val blob = ByteArrayOutputStream().use { baos ->
            ObjectOutputStream(baos).use { oos ->
                oos.writeObject(ex)
            }
            baos.toByteArray()
        }

        // deserialize
        ByteArrayInputStream(blob).use { bais ->
            ObjectInputStream(bais).use { ois ->
                val actual = ois.readObject() as DavException
                assertEquals(ex.message, actual.message)
                assertEquals(ex.statusCode, actual.statusCode)
                assertEquals(ex.requestExcerpt, actual.requestExcerpt)
                assertEquals(ex.responseExcerpt, actual.responseExcerpt)
                assertEquals(ex.errors, actual.errors)
                assertTrue(actual.cause is FileNotFoundException)
            }
        }
    }

}