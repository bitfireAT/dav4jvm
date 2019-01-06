/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm

import okhttp3.HttpUrl
import org.junit.Assert.assertEquals
import org.junit.Test

@Suppress("DEPRECATION")
class HttpUtilsTest {

    @Test
    fun testFilename() {
        val sampleUrl = HttpUrl.parse("https://example.com")!!
        assertEquals("", HttpUtils.fileName(sampleUrl.resolve("/")!!))
        assertEquals("hier1", HttpUtils.fileName(sampleUrl.resolve("/hier1")!!))
        assertEquals("", HttpUtils.fileName(sampleUrl.resolve("/hier1/")!!))
        assertEquals("hier2", HttpUtils.fileName(sampleUrl.resolve("/hier2")!!))
        assertEquals("", HttpUtils.fileName(sampleUrl.resolve("/hier2/")!!))
    }

    @Test
    fun testParseWwwAuthenticate() {
        // two schemes: one without param (illegal!), second with two params
        var schemes = HttpUtils.parseWwwAuthenticate(listOf("  UnknownWithoutParam,   Unknown   WithParam1=\"a\",   Param2  "))
        assertEquals(2, schemes.size)
        assertEquals("UnknownWithoutParam", schemes.first().name)
        assertEquals(0, schemes.first().params.size)
        assertEquals(0, schemes.first().unnamedParams.size)

        assertEquals("Unknown", schemes[1].name)
        assertEquals(1, schemes[1].params.size)
        assertEquals("a", schemes[1].params["withparam1"])
        assertEquals(1, schemes[1].params.size)
        assertEquals(1, schemes[1].unnamedParams.size)
        assertEquals("Param2", schemes[1].unnamedParams.first)

        // parameters with quoted strings with commas
        schemes = HttpUtils.parseWwwAuthenticate(listOf("X-MyScheme param1, param2=\"a,\\\"b\\\",c\", MyOtherScheme paramA"))
        assertEquals(2, schemes.size)
        assertEquals("X-MyScheme", schemes.first().name)
        assertEquals(1, schemes.first().params.size)
        assertEquals("a,\"b\",c", schemes.first().params["param2"])
        assertEquals(1, schemes.first().unnamedParams.size)
        assertEquals("param1", schemes.first().unnamedParams.first)

        assertEquals("MyOtherScheme", schemes[1].name)
        assertEquals(0, schemes[1].params.size)
        assertEquals(1, schemes[1].unnamedParams.size)
        assertEquals("paramA", schemes[1].unnamedParams.first)


        /*** REAL WORLD EXAMPLES ***/

        // Basic auth
        schemes = HttpUtils.parseWwwAuthenticate(listOf("Basic realm=\"test\""))
        assertEquals(1, schemes.size)
        var scheme = schemes.first()
        assertEquals("Basic", scheme.name)
        assertEquals(1, scheme.params.size)
        assertEquals("test", scheme.params["realm"])
        assertEquals(0, scheme.unnamedParams.size)

        // Basic and Digest auth in one line
        schemes = HttpUtils.parseWwwAuthenticate(listOf("Basic realm=\"testrealm@host.com\", Digest realm=\"testrealm@host.com\", qop=\"auth,auth-int\", nonce=\"dcd98b7102dd2f0e8b11d0f600bfb0c093\", opaque=\"5ccc069c403ebaf9f0171e9517f40e41\""))
        assertEquals(2, schemes.size)
        scheme = schemes.first()
        assertEquals("Basic", scheme.name)
        assertEquals(1, scheme.params.size)
        assertEquals("testrealm@host.com", scheme.params["realm"])
        assertEquals(0, scheme.unnamedParams.size)

        scheme = schemes[1]
        assertEquals("Digest", scheme.name)
        assertEquals(4, scheme.params.size)
        assertEquals("testrealm@host.com", scheme.params["realm"])
        assertEquals("auth,auth-int", scheme.params["qop"])
        assertEquals("dcd98b7102dd2f0e8b11d0f600bfb0c093", scheme.params["nonce"])
        assertEquals("5ccc069c403ebaf9f0171e9517f40e41", scheme.params["opaque"])
        assertEquals(0, scheme.unnamedParams.size)

        // Negotiate (RFC 4559)
        schemes = HttpUtils.parseWwwAuthenticate(listOf("Negotiate"))
        assertEquals(1, schemes.size)
        scheme = schemes.first()
        assertEquals("Negotiate", scheme.name)
        assertEquals(0, scheme.params.size)
        assertEquals(0, scheme.unnamedParams.size)

        schemes = HttpUtils.parseWwwAuthenticate(listOf("Negotiate a87421000492aa874209af8bc028"))
        assertEquals(1, schemes.size)
        scheme = schemes.first()
        assertEquals("Negotiate", scheme.name)
        assertEquals(0, scheme.params.size)
        assertEquals(1, scheme.unnamedParams.size)
        assertEquals("a87421000492aa874209af8bc028", scheme.unnamedParams.first)

        // NTLM, see https://msdn.microsoft.com/en-us/library/dd944123%28v=office.12%29.aspx
        schemes = HttpUtils.parseWwwAuthenticate(listOf(
                "NTLM realm=\"SIP Communications Service\", targetname=\"server.contoso.com\", version=3",
                "Kerberos realm=\"SIP Communications Service\", targetname=\"sip/server.contoso.com\", version=3"
        ))
        assertEquals(2, schemes.size)
        scheme = schemes.first()
        assertEquals("NTLM", scheme.name)
        assertEquals(3, scheme.params.size)
        assertEquals("SIP Communications Service", scheme.params["realm"])
        assertEquals("server.contoso.com", scheme.params["targetname"])
        assertEquals("3", scheme.params["version"])
        assertEquals(0, scheme.unnamedParams.size)

        scheme = schemes[1]
        assertEquals("Kerberos", scheme.name)
        assertEquals(3, scheme.params.size)
        assertEquals("SIP Communications Service", scheme.params["realm"])
        assertEquals("sip/server.contoso.com", scheme.params["targetname"])
        assertEquals("3", scheme.params["version"])
        assertEquals(0, scheme.unnamedParams.size)

        // https://issues.apache.org/jira/browse/HTTPCLIENT-1489
        schemes = HttpUtils.parseWwwAuthenticate(listOf("X-MobileMe-AuthToken realm=\"Newcastle\", Basic realm=\"Newcastle\""))
        assertEquals(2, schemes.size)
        scheme = schemes.first()
        assertEquals("X-MobileMe-AuthToken", scheme.name)
        assertEquals(1, scheme.params.size)
        assertEquals("Newcastle", scheme.params["realm"])
        assertEquals(0, scheme.unnamedParams.size)

        scheme = schemes[1]
        assertEquals("Basic", scheme.name)
        assertEquals(1, scheme.params.size)
        assertEquals("Newcastle", scheme.params["realm"])
        assertEquals(0, scheme.unnamedParams.size)

        // Contacts and Calendar Server example; space in second token!
        schemes = HttpUtils.parseWwwAuthenticate(listOf("digest nonce=\"785592012006934833760823299624355448128925071071026584347\", realm=\"Test Realm\", algorithm=\"md5\""))
        assertEquals(1, schemes.size)
        scheme = schemes.first()
        assertEquals("digest", scheme.name)
        assertEquals(3, scheme.params.size)
        assertEquals("785592012006934833760823299624355448128925071071026584347", scheme.params["nonce"])
        assertEquals("Test Realm", scheme.params["realm"])
        assertEquals("md5", scheme.params["algorithm"])
    }

}
