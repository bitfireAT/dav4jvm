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
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

class HttpExceptionTest {

    private val sampleUrl = "https://example.com"

    @Test
    fun isRedirect() {

        val mockEngine = MockEngine { request ->
            respond(
                status = HttpStatusCode.Found,
                content = "Your Information",
                headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Plain.toString())
            )
        }
        val httpClient = HttpClient(mockEngine) {
            followRedirects = false
        }

        runBlocking {
            val response = httpClient.get(sampleUrl)
            val result = HttpException(response, "Message")

            assertTrue(result.isRedirect)
            assertFalse(result.isClientError)
            assertFalse(result.isServerError)
        }
    }

    @Test
    fun isClientError() {

        val mockEngine = MockEngine { request ->
            respond(
                status = HttpStatusCode.NotFound,
                content = "Your Information",
                headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Plain.toString())
            )
        }
        val httpClient = HttpClient(mockEngine) {
            followRedirects = false
        }

        runBlocking {
            val response = httpClient.get(sampleUrl)
            val result = HttpException(response, "Message")

            assertFalse(result.isRedirect)
            assertTrue(result.isClientError)
            assertFalse(result.isServerError)
        }
    }

    @Test
    fun isServerError() {

        val mockEngine = MockEngine { request ->
            respond(
                status = HttpStatusCode.InternalServerError,
                content = "Your Information",
                headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Plain.toString())
            )
        }
        val httpClient = HttpClient(mockEngine) {
            followRedirects = false
        }

        runBlocking {
            val response = httpClient.get(sampleUrl)
            val result = HttpException(response, "Message")

            assertFalse(result.isRedirect)
            assertFalse(result.isClientError)
            assertTrue(result.isServerError)
        }
    }

    @Test
    fun `is Java-serializable`() {
        val ex = HttpException(
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
                val actual = ois.readObject() as HttpException
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