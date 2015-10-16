/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.dav4android;

import junit.framework.TestCase;

import java.util.List;

public class HttpUtilsTest extends TestCase {

    public void testParseWwwAuthenticate() {
        // two schemes: one without param (illegal!), second with two params
        List<HttpUtils.AuthScheme> schemes = HttpUtils.parseWwwAuthenticate(new String[]{ "  UnknownWithoutParam,   Unknown   WithParam1=\"a\",   Param2  " });
        assertEquals(2, schemes.size());
        assertEquals("UnknownWithoutParam", schemes.get(0).name);
        assertEquals(0, schemes.get(0).params.size());
        assertEquals(0, schemes.get(0).unnamedParams.size());

        assertEquals("Unknown", schemes.get(1).name);
        assertEquals(1, schemes.get(1).params.size());
        assertEquals("a", schemes.get(1).params.get("WithParam1"));
        assertEquals(1, schemes.get(1).params.size());
        assertEquals(1, schemes.get(1).unnamedParams.size());
        assertEquals("Param2", schemes.get(1).unnamedParams.get(0));

        // parameters with quoted strings with commas
         schemes = HttpUtils.parseWwwAuthenticate(new String[]{ "X-MyScheme param1, param2=\"a,\\\"b\\\",c\", MyOtherScheme paramA" });
        assertEquals(2, schemes.size());
        assertEquals("X-MyScheme", schemes.get(0).name);
        assertEquals(1, schemes.get(0).params.size());
        assertEquals("a,\"b\",c", schemes.get(0).params.get("param2"));
        assertEquals(1, schemes.get(0).unnamedParams.size());
        assertEquals("param1", schemes.get(0).unnamedParams.get(0));

        assertEquals("MyOtherScheme", schemes.get(1).name);
        assertEquals(0, schemes.get(1).params.size());
        assertEquals(1, schemes.get(1).unnamedParams.size());
        assertEquals("paramA", schemes.get(1).unnamedParams.get(0));


        /*** REAL WORLD EXAMPLES ***/

        // Basic auth
        schemes = HttpUtils.parseWwwAuthenticate(new String[]{ "Basic realm=\"test\"" });
        assertEquals(1, schemes.size());
        HttpUtils.AuthScheme scheme = schemes.get(0);
        assertEquals("Basic", scheme.name);
        assertEquals(1, scheme.params.size());
        assertEquals("test", scheme.params.get("realm"));
        assertEquals(0, scheme.unnamedParams.size());

        // Basic and Digest auth in one line
        schemes = HttpUtils.parseWwwAuthenticate(new String[]{ "Basic realm=\"testrealm@host.com\", Digest realm=\"testrealm@host.com\", qop=\"auth,auth-int\", nonce=\"dcd98b7102dd2f0e8b11d0f600bfb0c093\", opaque=\"5ccc069c403ebaf9f0171e9517f40e41\"" });
        assertEquals(2, schemes.size());
        scheme = schemes.get(0);
        assertEquals("Basic", scheme.name);
        assertEquals(1, scheme.params.size());
        assertEquals("testrealm@host.com", scheme.params.get("realm"));
        assertEquals(0, scheme.unnamedParams.size());

        scheme = schemes.get(1);
        assertEquals("Digest", scheme.name);
        assertEquals(4, scheme.params.size());
        assertEquals("testrealm@host.com", scheme.params.get("realm"));
        assertEquals("auth,auth-int", scheme.params.get("qop"));
        assertEquals("dcd98b7102dd2f0e8b11d0f600bfb0c093", scheme.params.get("nonce"));
        assertEquals("5ccc069c403ebaf9f0171e9517f40e41", scheme.params.get("opaque"));
        assertEquals(0, scheme.unnamedParams.size());

        // Negotiate (RFC 4559)
        schemes = HttpUtils.parseWwwAuthenticate(new String[]{ "Negotiate" });
        assertEquals(1, schemes.size());
        scheme = schemes.get(0);
        assertEquals("Negotiate", scheme.name);
        assertEquals(0, scheme.params.size());
        assertEquals(0, scheme.unnamedParams.size());

        schemes = HttpUtils.parseWwwAuthenticate(new String[]{ "Negotiate a87421000492aa874209af8bc028" });
        assertEquals(1, schemes.size());
        scheme = schemes.get(0);
        assertEquals("Negotiate", scheme.name);
        assertEquals(0, scheme.params.size());
        assertEquals(1, scheme.unnamedParams.size());
        assertEquals("a87421000492aa874209af8bc028", scheme.unnamedParams.get(0));

        // NTLM, see https://msdn.microsoft.com/en-us/library/dd944123%28v=office.12%29.aspx
        schemes = HttpUtils.parseWwwAuthenticate(new String[]{
                "NTLM realm=\"SIP Communications Service\", targetname=\"server.contoso.com\", version=3",
                "Kerberos realm=\"SIP Communications Service\", targetname=\"sip/server.contoso.com\", version=3"
        });
        assertEquals(2, schemes.size());
        scheme = schemes.get(0);
        assertEquals("NTLM", scheme.name);
        assertEquals(3, scheme.params.size());
        assertEquals("SIP Communications Service", scheme.params.get("realm"));
        assertEquals("server.contoso.com", scheme.params.get("targetname"));
        assertEquals("3", scheme.params.get("version"));
        assertEquals(0, scheme.unnamedParams.size());

        scheme = schemes.get(1);
        assertEquals("Kerberos", scheme.name);
        assertEquals(3, scheme.params.size());
        assertEquals("SIP Communications Service", scheme.params.get("realm"));
        assertEquals("sip/server.contoso.com", scheme.params.get("targetname"));
        assertEquals("3", scheme.params.get("version"));
        assertEquals(0, scheme.unnamedParams.size());

        // https://issues.apache.org/jira/browse/HTTPCLIENT-1489
        schemes = HttpUtils.parseWwwAuthenticate(new String[]{ "X-MobileMe-AuthToken realm=\"Newcastle\", Basic realm=\"Newcastle\"" });
        assertEquals(2, schemes.size());
        scheme = schemes.get(0);
        assertEquals("X-MobileMe-AuthToken", scheme.name);
        assertEquals(1, scheme.params.size());
        assertEquals("Newcastle", scheme.params.get("realm"));
        assertEquals(0, scheme.unnamedParams.size());

        scheme = schemes.get(1);
        assertEquals("Basic", scheme.name);
        assertEquals(1, scheme.params.size());
        assertEquals("Newcastle", scheme.params.get("realm"));
        assertEquals(0, scheme.unnamedParams.size());
    }

}
