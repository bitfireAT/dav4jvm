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

import at.bitfire.dav4jvm.ktor.KtorHttpUtils.INVALID_STATUS
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.ContentType
import io.ktor.http.HeadersBuilder
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KtorHttpUtilsTest {

    val sampleUrl = Url("http://127.0.0.1")
    private val multipleHeaderValues = "  1,  2 ,3,hyperactive-access"
    private val singleHeaderValue = "other"

    private fun getMockEngineWithDAVHeaderValues(vararg headerValues: String): HttpClient {
        val mockEngine = MockEngine { request ->
            respond("",HttpStatusCode.OK, HeadersBuilder().apply {
                headerValues.forEach { headerValue ->
                    append("DAV", headerValue)
                }
            }.build())
        }
        return HttpClient(mockEngine)
    }


    @Test
    fun fileName() {
        assertEquals("", KtorHttpUtils.fileName(Url("https://example.com")))
        assertEquals("", KtorHttpUtils.fileName(Url("https://example.com/")))
        assertEquals("file1", KtorHttpUtils.fileName(Url("https://example.com/file1")))
        assertEquals("dir1", KtorHttpUtils.fileName(Url("https://example.com/dir1/")))
        assertEquals("file2", KtorHttpUtils.fileName(Url("https://example.com/dir1/file2")))
        assertEquals("dir2", KtorHttpUtils.fileName(Url("https://example.com/dir1/dir2/")))
    }


    @Test
    fun `listHeader with a single header value`() = runTest {
        // Verify that when a header name has a single value, it's returned as a single-element array.
        val httpClient = getMockEngineWithDAVHeaderValues(singleHeaderValue)
        val headersArray = KtorHttpUtils.listHeader(httpClient.get(sampleUrl), "DAV")
        assertEquals(singleHeaderValue, headersArray[0])
        assertEquals(1, headersArray.size)
    }

    @Test
    fun `listHeader with multiple comma separated header values`() = runTest {
        // Verify that when a header name has multiple comma-separated values, they are correctly split into an array.
        val httpClient = getMockEngineWithDAVHeaderValues(multipleHeaderValues)
        val headersArray = KtorHttpUtils.listHeader(httpClient.get(sampleUrl), "DAV")
        assertEquals(4, headersArray.size)
        assertEquals("1", headersArray[0])
        assertEquals("2", headersArray[1])
        assertEquals("3", headersArray[2])
        assertEquals("hyperactive-access", headersArray[3])
    }

    @Test
    fun `listHeader with multiple distinct header entries for the same name`() = runTest {
        // Verify that if the same header name appears multiple times (e.g., 'Set-Cookie'), all values are joined by a comma and then split correctly.
        val httpClient = getMockEngineWithDAVHeaderValues(multipleHeaderValues, singleHeaderValue)
        val headersArray = KtorHttpUtils.listHeader(httpClient.get(sampleUrl), "DAV")
        assertEquals(5, headersArray.size)
        assertEquals("1", headersArray[0])
        assertEquals("2", headersArray[1])
        assertEquals("3", headersArray[2])
        assertEquals("hyperactive-access", headersArray[3])
        assertEquals("other", headersArray[4])
    }

    @Test
    fun `listHeader with a header name that does not exist`() = runTest {
        // Verify that when a requested header name is not present in the response, an empty array is returned.
        val httpClient = getMockEngineWithDAVHeaderValues(multipleHeaderValues, singleHeaderValue)
        val headersArray = KtorHttpUtils.listHeader(httpClient.get(sampleUrl), "other")
        assertEquals(0, headersArray.size)
    }

    @Test
    fun `listHeader with an empty header value`() = runTest {
        // Verify that if a header exists but its value is an empty string, an empty array is returned (due to filter { it.isNotEmpty() }).
        val httpClient = getMockEngineWithDAVHeaderValues("", "", "")
        val headersArray = KtorHttpUtils.listHeader(httpClient.get(sampleUrl), "DAV")
        assertEquals(0, headersArray.size)
    }

    @Test
    fun `listHeader with a header value containing only commas`() = runTest {
        // Verify that if a header value consists only of commas (e.g., ',,,') an empty array is returned.
        val httpClient = getMockEngineWithDAVHeaderValues(",", ",", ",")
        val headersArray = KtorHttpUtils.listHeader(httpClient.get(sampleUrl), "DAV")
        assertEquals(0, headersArray.size)
    }


    @Test
    fun `listHeader with header values that are themselves empty after splitting`() = runTest {
        // Verify that if a header value is like 'value1,,value2', the empty string between commas is filtered out, resulting in ['value1', 'value2'].
        val httpClient = getMockEngineWithDAVHeaderValues("   ", singleHeaderValue, ", ")
        val headersArray = KtorHttpUtils.listHeader(httpClient.get(sampleUrl), "DAV")
        assertEquals(1, headersArray.size)
        assertEquals(singleHeaderValue, headersArray[0])
    }

    @Test
    fun `listHeader with a case insensitive header name`() = runTest {
        // HTTP header names are case-insensitive. Verify that `response.headers.getAll(name)` correctly retrieves the header regardless of the casing used for `name` (e.g., 'Content-Type' vs 'content-type').
        val httpClient = getMockEngineWithDAVHeaderValues(singleHeaderValue)
        val headersArray = KtorHttpUtils.listHeader(httpClient.get(sampleUrl), "dav")
        assertEquals(singleHeaderValue, headersArray[0])
        assertEquals(1, headersArray.size)
    }

    @Test
    fun `listHeader with an empty string as header name`() = runTest {
        // Test what happens if an empty string is passed as the header name. This depends on how `response.headers.getAll` handles empty keys.
        val httpClient = getMockEngineWithDAVHeaderValues(singleHeaderValue)
        val headersArray = KtorHttpUtils.listHeader(httpClient.get(sampleUrl), "")
        assertEquals(0, headersArray.size)
    }


    @Test
    fun `Full status line with HTTP 1 1  200 code  and description`() =
        assertEquals(HttpStatusCode.OK, KtorHttpUtils.parseStatusLine("HTTP/1.1 200 OK"))

    @Test
    fun `Full status line with HTTP 1 0  404 code  and description`() =
        assertEquals(HttpStatusCode.NotFound, KtorHttpUtils.parseStatusLine("HTTP/1.0 404 Not Found"))

    @Test
    fun `Full status line with HTTP 2  503 code  and multi word description`() =
        assertEquals(HttpStatusCode.ServiceUnavailable, KtorHttpUtils.parseStatusLine("HTTP/2 503 Service Unavailable"))

    @Test
    fun `Full status line with HTTP 1 1  200 code  and no description`() =
        assertEquals(HttpStatusCode.OK, KtorHttpUtils.parseStatusLine("HTTP/1.1 200"))

    @Test
    fun `Partial status line with code and description`() =
        assertEquals(HttpStatusCode.OK, KtorHttpUtils.parseStatusLine("HTTP/1.1 200 OK"))

    @Test
    fun `Partial status line with code and multi word description`() =
        assertEquals(HttpStatusCode.NotFound, KtorHttpUtils.parseStatusLine("404 Not Found"))

    @Test
    fun `Partial status line with only code  200`() =
        assertEquals(HttpStatusCode.OK, KtorHttpUtils.parseStatusLine("200"))


    @Test
    fun `Partial status line with only code 404`() =
        assertEquals(HttpStatusCode.NotFound, KtorHttpUtils.parseStatusLine("404"))


    @Test
    fun `Partial status line with only a known code not having a default description`() =
        assertEquals(HttpStatusCode(303, ""), KtorHttpUtils.parseStatusLine("303"))

    @Test
    fun `Partial status line with only an unknown code`() =
        assertEquals(HttpStatusCode(999, ""), KtorHttpUtils.parseStatusLine("999"))

    @Test
    fun `Invalid status line   empty string`() =
        assertEquals(INVALID_STATUS, KtorHttpUtils.parseStatusLine(""))


    @Test
    fun `Invalid status line   just HTTP version`() =
        assertEquals(INVALID_STATUS, KtorHttpUtils.parseStatusLine("HTTP/1.1"))

    @Test
    fun `Invalid status line   HTTP version and non numeric code`() =
        assertEquals(INVALID_STATUS, KtorHttpUtils.parseStatusLine("HTTP/1.1 ABC OK"))

    @Test
    fun `Invalid status line   partial with non numeric code`() =
        assertEquals(INVALID_STATUS, KtorHttpUtils.parseStatusLine("ABC OK"))

    @Test
    fun `Invalid status line   just non numeric text`() =
        assertEquals(INVALID_STATUS, KtorHttpUtils.parseStatusLine("Invalid"))


    @Test
    fun `Invalid status line   HTTP version malformed`() =
        assertEquals(INVALID_STATUS, KtorHttpUtils.parseStatusLine("HTTP1.1 200 OK"))
    // Test a status line with a malformed HTTP version: 'HTTP1.1 200 OK'. Expects INVALID_STATUS (as it will be treated as parts.size == 3 but parts[0] doesn't start with 'HTTP/').

    @Test
    fun `Invalid status line   status code with leading trailing spaces`() =
        assertEquals(INVALID_STATUS, KtorHttpUtils.parseStatusLine("HTTP/1.1  200  OK"))

    @Test
    fun `Invalid status line   partial code with leading trailing spaces`() =
        assertEquals(INVALID_STATUS, KtorHttpUtils.parseStatusLine(" 200 "))

    @Test
    fun `Status line with extra spaces between code and description`() =
        assertEquals(HttpStatusCode.OK, KtorHttpUtils.parseStatusLine("HTTP/1.1 200   OK"))


    @Test
    fun `Partial status line with extra spaces between code and description`() =
        assertEquals(HttpStatusCode.OK, KtorHttpUtils.parseStatusLine("200   OK"))

    @Test
    fun `Full status line with negative status code`() =
        assertEquals(INVALID_STATUS, KtorHttpUtils.parseStatusLine("HTTP/1.1 -100 Error"))


    @Test
    fun `Partial status line with negative status code`() =
        assertEquals(INVALID_STATUS, KtorHttpUtils.parseStatusLine("-100"))

    @Test
    fun `Status line with only spaces`() =
        assertEquals(INVALID_STATUS, KtorHttpUtils.parseStatusLine("        "))


    @Test
    fun `Status line with special characters in description`() =
        assertEquals(HttpStatusCode(200, "Description with !@#$%^&*()"), KtorHttpUtils.parseStatusLine("HTTP/1.1 200 Description with !@#$%^&*()"))

    @Test
    fun `Status line with numeric description only  partial case `() =
        assertEquals(HttpStatusCode(200, "404"), KtorHttpUtils.parseStatusLine("HTTP/1.1 200 404"))


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
    fun `toUrlOrNull with invalid mailto URL`() {
        assertNull("mailto:invalid".toUrlOrNull())
    }

    @Test
    fun `toUrlOrNull with valid HTTPS URL`() {
        assertEquals(
            Url("https://example.com"),
            "https://example.com".toUrlOrNull()
        )
    }

    @Test
    fun `toUrlOrNull with valid relative URL`() {
        assertEquals(
            Url("relative"),
            "relative".toUrlOrNull()
        )
    }

}