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

import at.bitfire.dav4jvm.XmlUtils
import io.ktor.http.Url
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.StringReader

class ResponseParserTest {

    private val baseUrl = Url("http://www.example.com/container/")
    private val parser = ResponseParser(baseUrl)


    @Test
    fun `parseResponse relation=SELF`() = runTest {
        val xml = XmlUtils.newPullParser()
        xml.setInput(StringReader("<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
                "<multistatus xmlns=\"DAV:\">\n" +
                "<response>\n" +
                "  <href>http://www.example.com/container/</href>\n" +
                "  <propstat>\n" +
                "    <prop xmlns:R=\"http://ns.example.com/boxschema/\">\n" +
                "      <R:bigbox/>\n" +
                "      <R:author/>\n" +
                "      <creationdate/>\n" +
                "      <displayname/>\n" +
                "      <resourcetype/>\n" +
                "      <supportedlock/>\n" +
                "    </prop>\n" +
                "    <status>HTTP/1.1 200 OK</status>\n" +
                "  </propstat>\n" +
                "</response>"
        ))
        xml.nextTag()   // multistatus
        xml.nextTag()   // response
        parser.parseResponse(xml) { response, relation ->
            assertEquals(Url("http://www.example.com/container/"), response.href)
            assertEquals(Response.HrefRelation.SELF, relation)
        }
    }

    @Test
    fun `parseResponse relation=MEMBER`() = runTest {
        val xml = XmlUtils.newPullParser()
        xml.setInput(StringReader("<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
                "<multistatus xmlns=\"DAV:\">\n" +
                "<response>\n" +
                "  <href>http://www.example.com/container/front.html</href>\n" +
                "  <propstat>\n" +
                "    <prop xmlns:R=\"http://ns.example.com/boxschema/\">\n" +
                "      <R:bigbox/>\n" +
                "      <creationdate/>\n" +
                "      <displayname/>\n" +
                "      <getcontentlength/>\n" +
                "      <getcontenttype/>\n" +
                "      <getetag/>\n" +
                "      <getlastmodified/>\n" +
                "      <resourcetype/>\n" +
                "      <supportedlock/>\n" +
                "    </prop>\n" +
                "    <status>HTTP/1.1 200 OK</status>\n" +
                "  </propstat>\n" +
                "</response>"
        ))
        xml.nextTag()   // multistatus
        xml.nextTag()   // response
        parser.parseResponse(xml) { response, relation ->
            assertEquals(Url("http://www.example.com/container/front.html"), response.href)
            assertEquals(Response.HrefRelation.MEMBER, relation)
        }
    }

    @Test
    fun `parseResponse relation=OTHER`() = runTest {
        val xml = XmlUtils.newPullParser()
        xml.setInput(StringReader("<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
                "<multistatus xmlns=\"DAV:\">\n" +
                "<response>\n" +
                "  <href>http://other.example.com/was-not-requested</href>\n" +
                "  <propstat>\n" +
                "    <prop xmlns:R=\"http://ns.example.com/boxschema/\">\n" +
                "      <R:bigbox/>\n" +
                "      <creationdate/>\n" +
                "      <displayname/>\n" +
                "      <getcontentlength/>\n" +
                "      <getcontenttype/>\n" +
                "      <getetag/>\n" +
                "      <getlastmodified/>\n" +
                "      <resourcetype/>\n" +
                "      <supportedlock/>\n" +
                "    </prop>\n" +
                "    <status>HTTP/1.1 200 OK</status>\n" +
                "  </propstat>\n" +
                "</response>"
        ))
        xml.nextTag()   // multistatus
        xml.nextTag()   // response
        parser.parseResponse(xml) { response, relation ->
            assertEquals(Url("http://other.example.com/was-not-requested"), response.href)
            assertEquals(Response.HrefRelation.OTHER, relation)
        }
    }


    @Test
    fun `resolveHref with absolute URL`() {
        assertEquals(
            Url("http://www.example.com/container/member"),
            parser.resolveHref("http://www.example.com/container/member")
        )
    }

    @Test
    fun `resolveHref with absolute path`() {
        assertEquals(
            Url("http://www.example.com/container/member"),
            parser.resolveHref("/container/member")
        )
    }

    @Test
    fun `resolveHref with relative path`() {
        assertEquals(
            Url("http://www.example.com/container/member"),
            parser.resolveHref("member")
        )
    }

    @Test
    fun `resolveHref with relative path with colon`() {
        assertEquals(
            Url("http://www.example.com/container/mem:ber"),
            parser.resolveHref("mem:ber")
        )
    }

}