/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.dav4android;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class HttpUtilsTest {

    @Test
    public void testParseWwwAuthenticate() {
        // two schemes: one without param (illegal!), second with two params
        List<HttpUtils.AuthScheme> schemes = HttpUtils.parseWwwAuthenticate(Arrays.asList(new String[]{ "  UnknownWithoutParam,   Unknown   WithParam1=\"a\",   Param2  " }));
        assertEquals(2, schemes.size());
        assertEquals("UnknownWithoutParam", schemes.get(0).getName());
        assertEquals(0, schemes.get(0).getParams().size());
        assertEquals(0, schemes.get(0).getUnnamedParams().size());

        assertEquals("Unknown", schemes.get(1).getName());
        assertEquals(1, schemes.get(1).getParams().size());
        assertEquals("a", schemes.get(1).getParams().get("withparam1"));
        assertEquals(1, schemes.get(1).getParams().size());
        assertEquals(1, schemes.get(1).getUnnamedParams().size());
        assertEquals("Param2", schemes.get(1).getUnnamedParams().get(0));

        // parameters with quoted strings with commas
        schemes = HttpUtils.parseWwwAuthenticate(Arrays.asList(new String[]{ "X-MyScheme param1, param2=\"a,\\\"b\\\",c\", MyOtherScheme paramA" }));
        assertEquals(2, schemes.size());
        assertEquals("X-MyScheme", schemes.get(0).getName());
        assertEquals(1, schemes.get(0).getParams().size());
        assertEquals("a,\"b\",c", schemes.get(0).getParams().get("param2"));
        assertEquals(1, schemes.get(0).getUnnamedParams().size());
        assertEquals("param1", schemes.get(0).getUnnamedParams().get(0));

        assertEquals("MyOtherScheme", schemes.get(1).getName());
        assertEquals(0, schemes.get(1).getParams().size());
        assertEquals(1, schemes.get(1).getUnnamedParams().size());
        assertEquals("paramA", schemes.get(1).getUnnamedParams().get(0));


        /*** REAL WORLD EXAMPLES ***/

        // Basic auth
        schemes = HttpUtils.parseWwwAuthenticate(Arrays.asList(new String[]{ "Basic realm=\"test\"" }));
        assertEquals(1, schemes.size());
        HttpUtils.AuthScheme scheme = schemes.get(0);
        assertEquals("Basic", scheme.getName());
        assertEquals(1, scheme.getParams().size());
        assertEquals("test", scheme.getParams().get("realm"));
        assertEquals(0, scheme.getUnnamedParams().size());

        // Basic and Digest auth in one line
        schemes = HttpUtils.parseWwwAuthenticate(Arrays.asList(new String[]{ "Basic realm=\"testrealm@host.com\", Digest realm=\"testrealm@host.com\", qop=\"auth,auth-int\", nonce=\"dcd98b7102dd2f0e8b11d0f600bfb0c093\", opaque=\"5ccc069c403ebaf9f0171e9517f40e41\"" }));
        assertEquals(2, schemes.size());
        scheme = schemes.get(0);
        assertEquals("Basic", scheme.getName());
        assertEquals(1, scheme.getParams().size());
        assertEquals("testrealm@host.com", scheme.getParams().get("realm"));
        assertEquals(0, scheme.getUnnamedParams().size());

        scheme = schemes.get(1);
        assertEquals("Digest", scheme.getName());
        assertEquals(4, scheme.getParams().size());
        assertEquals("testrealm@host.com", scheme.getParams().get("realm"));
        assertEquals("auth,auth-int", scheme.getParams().get("qop"));
        assertEquals("dcd98b7102dd2f0e8b11d0f600bfb0c093", scheme.getParams().get("nonce"));
        assertEquals("5ccc069c403ebaf9f0171e9517f40e41", scheme.getParams().get("opaque"));
        assertEquals(0, scheme.getUnnamedParams().size());

        // Negotiate (RFC 4559)
        schemes = HttpUtils.parseWwwAuthenticate(Arrays.asList(new String[]{ "Negotiate" }));
        assertEquals(1, schemes.size());
        scheme = schemes.get(0);
        assertEquals("Negotiate", scheme.getName());
        assertEquals(0, scheme.getParams().size());
        assertEquals(0, scheme.getUnnamedParams().size());

        schemes = HttpUtils.parseWwwAuthenticate(Arrays.asList(new String[]{ "Negotiate a87421000492aa874209af8bc028" }));
        assertEquals(1, schemes.size());
        scheme = schemes.get(0);
        assertEquals("Negotiate", scheme.getName());
        assertEquals(0, scheme.getParams().size());
        assertEquals(1, scheme.getUnnamedParams().size());
        assertEquals("a87421000492aa874209af8bc028", scheme.getUnnamedParams().get(0));

        // NTLM, see https://msdn.microsoft.com/en-us/library/dd944123%28v=office.12%29.aspx
        schemes = HttpUtils.parseWwwAuthenticate(Arrays.asList(new String[]{
                "NTLM realm=\"SIP Communications Service\", targetname=\"server.contoso.com\", version=3",
                "Kerberos realm=\"SIP Communications Service\", targetname=\"sip/server.contoso.com\", version=3"
        }));
        assertEquals(2, schemes.size());
        scheme = schemes.get(0);
        assertEquals("NTLM", scheme.getName());
        assertEquals(3, scheme.getParams().size());
        assertEquals("SIP Communications Service", scheme.getParams().get("realm"));
        assertEquals("server.contoso.com", scheme.getParams().get("targetname"));
        assertEquals("3", scheme.getParams().get("version"));
        assertEquals(0, scheme.getUnnamedParams().size());

        scheme = schemes.get(1);
        assertEquals("Kerberos", scheme.getName());
        assertEquals(3, scheme.getParams().size());
        assertEquals("SIP Communications Service", scheme.getParams().get("realm"));
        assertEquals("sip/server.contoso.com", scheme.getParams().get("targetname"));
        assertEquals("3", scheme.getParams().get("version"));
        assertEquals(0, scheme.getUnnamedParams().size());

        // https://issues.apache.org/jira/browse/HTTPCLIENT-1489
        schemes = HttpUtils.parseWwwAuthenticate(Arrays.asList(new String[]{ "X-MobileMe-AuthToken realm=\"Newcastle\", Basic realm=\"Newcastle\"" }));
        assertEquals(2, schemes.size());
        scheme = schemes.get(0);
        assertEquals("X-MobileMe-AuthToken", scheme.getName());
        assertEquals(1, scheme.getParams().size());
        assertEquals("Newcastle", scheme.getParams().get("realm"));
        assertEquals(0, scheme.getUnnamedParams().size());

        scheme = schemes.get(1);
        assertEquals("Basic", scheme.getName());
        assertEquals(1, scheme.getParams().size());
        assertEquals("Newcastle", scheme.getParams().get("realm"));
        assertEquals(0, scheme.getUnnamedParams().size());

        // Contacts and Calendar Server example; space in second token!
        schemes = HttpUtils.parseWwwAuthenticate(Arrays.asList(new String[]{ "digest nonce=\"785592012006934833760823299624355448128925071071026584347\", realm=\"Test Realm\", algorithm=\"md5\"" }));
        assertEquals(1, schemes.size());
        scheme = schemes.get(0);
        assertEquals("digest", scheme.getName());
        assertEquals(3, scheme.getParams().size());
        assertEquals("785592012006934833760823299624355448128925071071026584347", scheme.getParams().get("nonce"));
        assertEquals("Test Realm", scheme.getParams().get("realm"));
        assertEquals("md5", scheme.getParams().get("algorithm"));
    }

}
