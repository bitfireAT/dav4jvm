/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

package at.bitfire.dav4jvm.okhttp.exception

import at.bitfire.dav4jvm.okhttp.Error
import at.bitfire.dav4jvm.Property
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

class DavExceptionTest {

    @Test
    fun fromResponse() {
        val request = Request.Builder()
            .get()
            .url("https://example.com")
            .build()
        val cause = Exception()
        val result = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body("Your Information".toResponseBody("text/plain".toMediaType()))
            .build()
            .use { response ->
                DavException("Message", cause, response)
            }
        assertEquals("Message", result.message)
        assertEquals(cause, result.cause)
        assertEquals(200, result.statusCode)
        assertEquals("GET https://example.com/", result.requestExcerpt)
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