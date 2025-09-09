/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

package at.bitfire.dav4jvm.exception

import at.bitfire.dav4jvm.Error
import at.bitfire.dav4jvm.Property
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
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

    @Test
    fun isRedirect() {
        val request = Request.Builder()
            .get()
            .url("https://example.com")
            .build()
        val result = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(302)
            .message("Found")
            .build()
            .use { response ->
                HttpException(response, "Message")
            }
        assertTrue(result.isRedirect)
        assertFalse(result.isClientError)
        assertFalse(result.isServerError)
    }

    @Test
    fun isClientError() {
        val request = Request.Builder()
            .get()
            .url("https://example.com")
            .build()
        val result = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(404)
            .message("Not Found")
            .build()
            .use { response ->
                HttpException(response, "Message")
            }
        assertFalse(result.isRedirect)
        assertTrue(result.isClientError)
        assertFalse(result.isServerError)
    }

    @Test
    fun isServerError() {
        val request = Request.Builder()
            .get()
            .url("https://example.com")
            .build()
        val result = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(500)
            .message("Internal Server Error")
            .build()
            .use { response ->
                HttpException(response, "Message")
            }
        assertFalse(result.isRedirect)
        assertFalse(result.isClientError)
        assertTrue(result.isServerError)
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