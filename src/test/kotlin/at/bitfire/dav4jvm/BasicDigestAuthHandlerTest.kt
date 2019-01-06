/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm

import okhttp3.*
import okhttp3.Response
import okhttp3.Response.Builder
import org.junit.Assert.*
import org.junit.Test

class BasicDigestAuthHandlerTest {

    @Test
    fun testBasic() {
        var authenticator = BasicDigestAuthHandler(null, "user", "password")
        val original = Request.Builder()
                        .url("http://example.com")
                        .build()
        var response = Builder()
                .request(original)
                .protocol(Protocol.HTTP_1_1)
                .code(401).message("Authentication required")
                .header("WWW-Authenticate", "Basic realm=\"WallyWorld\"")
                .build()
        var request = authenticator.authenticateRequest(original, response)
        assertEquals("Basic dXNlcjpwYXNzd29yZA==", request!!.header("Authorization"))

        // special characters: always use UTF-8 (and don't crash on RFC 7617 charset header)
        authenticator = BasicDigestAuthHandler(null, "username", "paßword")
        response = response.newBuilder()
                .header("WWW-Authenticate", "Basic realm=\"WallyWorld\",charset=UTF-8")
                .build()
        request = authenticator.authenticateRequest(original, response)
        assertEquals("Basic dXNlcm5hbWU6cGHDn3dvcmQ=", request!!.header("Authorization"))
    }

    @Test
    fun testDigestRFCExample() {
        // use cnonce from example
        val authenticator = BasicDigestAuthHandler(null, "Mufasa", "Circle Of Life")
        BasicDigestAuthHandler.clientNonce = "0a4f113b"
        BasicDigestAuthHandler.nonceCount.set(1)

        // construct WWW-Authenticate
        val authScheme = Challenge("Digest", mapOf(
                Pair("realm", "testrealm@host.com"),
                Pair("qop", "auth"),
                Pair("nonce", "dcd98b7102dd2f0e8b11d0f600bfb0c093"),
                Pair("opaque", "5ccc069c403ebaf9f0171e9517f40e41")
        ))

        val original = Request.Builder()
                .get()
                .url("http://www.nowhere.org/dir/index.html")
                .build()
        val request = authenticator.digestRequest(original, authScheme)
        val auth = request!!.header("Authorization")
        assertTrue(auth!!.contains("username=\"Mufasa\""))
        assertTrue(auth.contains("realm=\"testrealm@host.com\""))
        assertTrue(auth.contains("nonce=\"dcd98b7102dd2f0e8b11d0f600bfb0c093\""))
        assertTrue(auth.contains("uri=\"/dir/index.html\""))
        assertTrue(auth.contains("qop=auth"))
        assertTrue(auth.contains("nc=00000001"))
        assertTrue(auth.contains("cnonce=\"0a4f113b\""))
        assertTrue(auth.contains("response=\"6629fae49393a05397450978507c4ef1\""))
        assertTrue(auth.contains("opaque=\"5ccc069c403ebaf9f0171e9517f40e41\""))
    }

    @Test
    fun testDigestRealWorldExamples() {
        var authenticator = BasicDigestAuthHandler(null, "demo", "demo")
        BasicDigestAuthHandler.clientNonce = "MDI0ZDgxYTNmZDk4MTA1ODM0NDNjNmJjNDllYjQ1ZTI="
        BasicDigestAuthHandler.nonceCount.set(1)

        // example 1
        var authScheme = Challenge("Digest", mapOf(
                Pair("realm", "Group-Office"),
                Pair("qop", "auth"),
                Pair("nonce", "56212407212c8"),
                Pair("opaque", "df58bdff8cf60599c939187d0b5c54de")
        ))

        var original = Request.Builder()
                .method("PROPFIND", null)
                .url("https://demo.group-office.eu/caldav/")
                .build()
        var request = authenticator.digestRequest(original, authScheme)
        var auth = request!!.header("Authorization")
        assertTrue(auth!!.contains("username=\"demo\""))
        assertTrue(auth.contains("realm=\"Group-Office\""))
        assertTrue(auth.contains("nonce=\"56212407212c8\""))
        assertTrue(auth.contains("uri=\"/caldav/\""))
        assertTrue(auth.contains("cnonce=\"MDI0ZDgxYTNmZDk4MTA1ODM0NDNjNmJjNDllYjQ1ZTI=\""))
        assertTrue(auth.contains("nc=00000001"))
        assertTrue(auth.contains("qop=auth"))
        assertTrue(auth.contains("response=\"de3b3b194d85ddc62537208c9c3637dc\""))
        assertTrue(auth.contains("opaque=\"df58bdff8cf60599c939187d0b5c54de\""))

        // example 2
        authenticator = BasicDigestAuthHandler(null, "test", "test")
        authScheme = Challenge("digest", mapOf(    // lower case
                Pair("nonce", "87c4c2aceed9abf30dd68c71"),
                Pair("algorithm", "md5"),
                Pair("opaque", "571609eb7058505d35c7bf7288fbbec4-ODdjNGMyYWNlZWQ5YWJmMzBkZDY4YzcxLDAuMC4wLjAsMTQ0NTM3NzE0Nw=="),
                Pair("realm", "ieddy.ru")
        ))
        original = Request.Builder()
                .method("OPTIONS", null)
                .url("https://ieddy.ru/")
                .build()
        request = authenticator.digestRequest(original, authScheme)
        auth = request!!.header("Authorization")
        assertTrue(auth!!.contains("algorithm=\"MD5\""))     // some servers require it
        assertTrue(auth.contains("username=\"test\""))
        assertTrue(auth.contains("realm=\"ieddy.ru\""))
        assertTrue(auth.contains("nonce=\"87c4c2aceed9abf30dd68c71\""))
        assertTrue(auth.contains("uri=\"/\""))
        assertFalse(auth.contains("cnonce="))
        assertFalse(auth.contains("nc=00000001"))
        assertFalse(auth.contains("qop="))
        assertTrue(auth.contains("response=\"d42a39f25f80b0d6907286a960ff9c7d\""))
        assertTrue(auth.contains("opaque=\"571609eb7058505d35c7bf7288fbbec4-ODdjNGMyYWNlZWQ5YWJmMzBkZDY4YzcxLDAuMC4wLjAsMTQ0NTM3NzE0Nw==\""))
    }

    @Test
    fun testDigestMD5Sess() {
        val authenticator = BasicDigestAuthHandler(null, "admin", "12345")
        BasicDigestAuthHandler.clientNonce = "hxk1lu63b6c7vhk"
        BasicDigestAuthHandler.nonceCount.set(1)

        val authScheme = Challenge("Digest", mapOf(
                Pair("realm", "MD5-sess Example"),
                Pair("qop", "auth"),
                Pair("algorithm", "MD5-sess"),
                Pair("nonce", "dcd98b7102dd2f0e8b11d0f600bfb0c093"),
                Pair("opaque", "5ccc069c403ebaf9f0171e9517f40e41")
        ))

        /*  A1 = h("admin:MD5-sess Example:12345"):dcd98b7102dd2f0e8b11d0f600bfb0c093:hxk1lu63b6c7vhk =
                  4eaed818bc587129e73b39c8d3e8425a:dcd98b7102dd2f0e8b11d0f600bfb0c093:hxk1lu63b6c7vhk       a994ee9d33e2f077d3a6e13e882f6686
            A2 = POST:/plain.txt                                                                            1b557703454e1aa1230c5523f54380ed

            h("a994ee9d33e2f077d3a6e13e882f6686:dcd98b7102dd2f0e8b11d0f600bfb0c093:00000001:hxk1lu63b6c7vhk:auth:1b557703454e1aa1230c5523f54380ed") =
            af2a72145775cfd08c36ad2676e89446
        */

        val original = Request.Builder()
                .method("POST", RequestBody.create(MediaType.parse("text/plain"), "PLAIN TEXT"))
                .url("http://example.com/plain.txt")
                .build()
        val request = authenticator.digestRequest(original, authScheme)
        val auth = request!!.header("Authorization")
        assertTrue(auth!!.contains("username=\"admin\""))
        assertTrue(auth.contains("realm=\"MD5-sess Example\""))
        assertTrue(auth.contains("nonce=\"dcd98b7102dd2f0e8b11d0f600bfb0c093\""))
        assertTrue(auth.contains("uri=\"/plain.txt\""))
        assertTrue(auth.contains("cnonce=\"hxk1lu63b6c7vhk\""))
        assertTrue(auth.contains("nc=00000001"))
        assertTrue(auth.contains("qop=auth"))
        assertTrue(auth.contains("response=\"af2a72145775cfd08c36ad2676e89446\""))
        assertTrue(auth.contains("opaque=\"5ccc069c403ebaf9f0171e9517f40e41\""))
    }

    @Test
    fun testDigestMD5AuthInt() {
        val authenticator = BasicDigestAuthHandler(null, "admin", "12435")
        BasicDigestAuthHandler.clientNonce = "hxk1lu63b6c7vhk"
        BasicDigestAuthHandler.nonceCount.set(1)

        val authScheme = Challenge("Digest", mapOf(
                Pair("realm", "AuthInt Example"),
                Pair("qop", "auth-int"),
                Pair("nonce", "367sj3265s5"),
                Pair("opaque", "87aaxcval4gba36")
        ))

        /*  A1 = admin:AuthInt Example:12345                            380dc3fc1305127cd2aa81ab68ef3f34

            h("PLAIN TEXT") = 20296edbd4c4275fb416b64e4be752f9
            A2 = POST:/plain.txt:20296edbd4c4275fb416b64e4be752f9       a71c4c86e18b3993ffc98c6e426fe4b0

            h(380dc3fc1305127cd2aa81ab68ef3f34:367sj3265s5:00000001:hxk1lu63b6c7vhk:auth-int:a71c4c86e18b3993ffc98c6e426fe4b0) =
            81d07cb3b8d412b34144164124c970cb
        */

        val original = Request.Builder()
                .method("POST", RequestBody.create(MediaType.parse("text/plain"), "PLAIN TEXT"))
                .url("http://example.com/plain.txt")
                .build()
        val request = authenticator.digestRequest(original, authScheme)
        val auth = request!!.header("Authorization")
        assertTrue(auth!!.contains("username=\"admin\""))
        assertTrue(auth.contains("realm=\"AuthInt Example\""))
        assertTrue(auth.contains("nonce=\"367sj3265s5\""))
        assertTrue(auth.contains("uri=\"/plain.txt\""))
        assertTrue(auth.contains("cnonce=\"hxk1lu63b6c7vhk\""))
        assertTrue(auth.contains("nc=00000001"))
        assertTrue(auth.contains("qop=auth-int"))
        assertTrue(auth.contains("response=\"5ab6822b9d906cc711760a7783b28dca\""))
        assertTrue(auth.contains("opaque=\"87aaxcval4gba36\""))
    }

    @Test
    fun testDigestLegacy() {
        val authenticator = BasicDigestAuthHandler(null, "Mufasa", "CircleOfLife")

        // construct WWW-Authenticate
        val authScheme = Challenge("Digest", mapOf(
                Pair("realm", "testrealm@host.com"),
                Pair("nonce", "dcd98b7102dd2f0e8b11d0f600bfb0c093"),
                Pair("opaque", "5ccc069c403ebaf9f0171e9517f40e41")
        ))

        val original = Request.Builder()
                .get()
                .url("http://www.nowhere.org/dir/index.html")
                .build()
        val request = authenticator.digestRequest(original, authScheme)
        val auth = request!!.header("Authorization")
        assertTrue(auth!!.contains("username=\"Mufasa\""))
        assertTrue(auth.contains("realm=\"testrealm@host.com\""))
        assertTrue(auth.contains("nonce=\"dcd98b7102dd2f0e8b11d0f600bfb0c093\""))
        assertTrue(auth.contains("uri=\"/dir/index.html\""))
        assertFalse(auth.contains("qop="))
        assertFalse(auth.contains("nc="))
        assertFalse(auth.contains("cnonce="))
        assertTrue(auth.contains("response=\"1949323746fe6a43ef61f9606e7febea\""))
        assertTrue(auth.contains("opaque=\"5ccc069c403ebaf9f0171e9517f40e41\""))
    }

    @Test
    fun testIncompleteAuthenticationRequests() {
        val authenticator = BasicDigestAuthHandler(null, "demo", "demo")

        val original = Request.Builder()
                .get()
                .url("http://www.nowhere.org/dir/index.html")
                .build()

        assertNull(authenticator.digestRequest(original, Challenge("Digest", mapOf())))

        assertNull(authenticator.digestRequest(original, Challenge("Digest", mapOf(
                Pair("realm", "Group-Office")
        ))))

        assertNull(authenticator.digestRequest(original, Challenge("Digest", mapOf(
                Pair("realm", "Group-Office"),
                Pair("qop", "auth")
        ))))

        assertNotNull(authenticator.digestRequest(original, Challenge("Digest", mapOf(
                Pair("realm", "Group-Office"),
                Pair("qop", "auth"),
                Pair("nonce", "56212407212c8")
        ))))
    }

    @Test
    fun testAuthenticateNull() {
        val authenticator = BasicDigestAuthHandler(null, "demo", "demo")
        // must not crash (route may be null)
        val request = Request.Builder()
                .get()
                .url("http://example.com")
                .build()
        val response = Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_2)
                .code(200).message("OK")
                .build()
        authenticator.authenticate(null, response)
    }

}
