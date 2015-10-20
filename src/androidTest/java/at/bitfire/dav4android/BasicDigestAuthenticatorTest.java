/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.dav4android;

import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;

import junit.framework.TestCase;

public class BasicDigestAuthenticatorTest extends TestCase {

    public void testRFCExample() {
        // use cnonce from example
        BasicDigestAuthenticator authenticator = new BasicDigestAuthenticator(null, "Mufasa", "Circle Of Life", "0a4f113b");

        // construct WWW-Authenticate
        HttpUtils.AuthScheme authScheme = new HttpUtils.AuthScheme("Digest");
        authScheme.params.put("realm", "testrealm@host.com");
        authScheme.params.put("qop", "auth");
        authScheme.params.put("nonce", "dcd98b7102dd2f0e8b11d0f600bfb0c093");
        authScheme.params.put("opaque", "5ccc069c403ebaf9f0171e9517f40e41");

        Request original = new Request.Builder()
                .get()
                .url("http://www.nowhere.org/dir/index.html")
                .build();
        Request request = authenticator.authorizationRequest(original, authScheme);
        String auth = request.header("Authorization");
        assertTrue(auth.contains("username=\"Mufasa\""));
        assertTrue(auth.contains("realm=\"testrealm@host.com\""));
        assertTrue(auth.contains("nonce=\"dcd98b7102dd2f0e8b11d0f600bfb0c093\""));
        assertTrue(auth.contains("uri=\"/dir/index.html\""));
        assertTrue(auth.contains("qop=auth"));
        assertTrue(auth.contains("nc=00000001"));
        assertTrue(auth.contains("cnonce=\"0a4f113b\""));
        assertTrue(auth.contains("response=\"6629fae49393a05397450978507c4ef1\""));
        assertTrue(auth.contains("opaque=\"5ccc069c403ebaf9f0171e9517f40e41\""));
    }

    public void testRealWorldExamples() {
        BasicDigestAuthenticator authenticator = new BasicDigestAuthenticator(null, "demo", "demo", "MDI0ZDgxYTNmZDk4MTA1ODM0NDNjNmJjNDllYjQ1ZTI=");

        // example 1
        HttpUtils.AuthScheme authScheme = new HttpUtils.AuthScheme("Digest");
        authScheme.params.put("realm", "Group-Office");
        authScheme.params.put("qop", "auth");
        authScheme.params.put("nonce", "56212407212c8");
        authScheme.params.put("opaque", "df58bdff8cf60599c939187d0b5c54de");

        Request original = new Request.Builder()
                .method("PROPFIND", null)
                .url("https://demo.group-office.eu/caldav/")
                .build();
        Request request = authenticator.authorizationRequest(original, authScheme);
        String auth = request.header("Authorization");
        assertTrue(auth.contains("username=\"demo\""));
        assertTrue(auth.contains("realm=\"Group-Office\""));
        assertTrue(auth.contains("nonce=\"56212407212c8\""));
        assertTrue(auth.contains("uri=\"/caldav/\""));
        assertTrue(auth.contains("cnonce=\"MDI0ZDgxYTNmZDk4MTA1ODM0NDNjNmJjNDllYjQ1ZTI=\""));
        assertTrue(auth.contains("nc=00000001"));
        assertTrue(auth.contains("qop=auth"));
        assertTrue(auth.contains("response=\"de3b3b194d85ddc62537208c9c3637dc\""));
        assertTrue(auth.contains("opaque=\"df58bdff8cf60599c939187d0b5c54de\""));

        // example 2
        authenticator = new BasicDigestAuthenticator(null, "test", "test");
        authScheme = new HttpUtils.AuthScheme("digest");    // lower case
        authScheme.params.put("nonce", "87c4c2aceed9abf30dd68c71");
        authScheme.params.put("algorithm", "md5");          // note the (illegal) lower case!
        authScheme.params.put("opaque", "571609eb7058505d35c7bf7288fbbec4-ODdjNGMyYWNlZWQ5YWJmMzBkZDY4YzcxLDAuMC4wLjAsMTQ0NTM3NzE0Nw==");
        authScheme.params.put("realm", "ieddy.ru");
        original = new Request.Builder()
                .method("OPTIONS", null)
                .url("https://ieddy.ru/")
                .build();
        request = authenticator.authorizationRequest(original, authScheme);
        auth = request.header("Authorization");
        assertTrue(auth.contains("algorithm=\"MD5\""));     // some servers require it
        assertTrue(auth.contains("username=\"test\""));
        assertTrue(auth.contains("realm=\"ieddy.ru\""));
        assertTrue(auth.contains("nonce=\"87c4c2aceed9abf30dd68c71\""));
        assertTrue(auth.contains("uri=\"/\""));
        assertFalse(auth.contains("cnonce="));
        assertFalse(auth.contains("nc="));
        assertFalse(auth.contains("qop="));
        assertTrue(auth.contains("response=\"d42a39f25f80b0d6907286a960ff9c7d\""));
        assertTrue(auth.contains("opaque=\"571609eb7058505d35c7bf7288fbbec4-ODdjNGMyYWNlZWQ5YWJmMzBkZDY4YzcxLDAuMC4wLjAsMTQ0NTM3NzE0Nw==\""));
    }

    public void testMD5Sess() {
        BasicDigestAuthenticator authenticator = new BasicDigestAuthenticator(null, "admin", "12345", "hxk1lu63b6c7vhk");

        HttpUtils.AuthScheme authScheme = new HttpUtils.AuthScheme("Digest");
        authScheme.params.put("realm", "MD5-sess Example");
        authScheme.params.put("qop", "auth");
        authScheme.params.put("algorithm", "MD5-sess");
        authScheme.params.put("nonce", "dcd98b7102dd2f0e8b11d0f600bfb0c093");
        authScheme.params.put("opaque", "5ccc069c403ebaf9f0171e9517f40e41");

        /*  A1 = h("admin:MD5-sess Example:12345"):dcd98b7102dd2f0e8b11d0f600bfb0c093:hxk1lu63b6c7vhk =
                  4eaed818bc587129e73b39c8d3e8425a:dcd98b7102dd2f0e8b11d0f600bfb0c093:hxk1lu63b6c7vhk       a994ee9d33e2f077d3a6e13e882f6686
            A2 = POST:/plain.txt                                                                            1b557703454e1aa1230c5523f54380ed

            h("a994ee9d33e2f077d3a6e13e882f6686:dcd98b7102dd2f0e8b11d0f600bfb0c093:00000001:hxk1lu63b6c7vhk:auth:1b557703454e1aa1230c5523f54380ed") =
            af2a72145775cfd08c36ad2676e89446
        */

        Request original = new Request.Builder()
                .method("POST", RequestBody.create(MediaType.parse("text/plain"), "PLAIN TEXT"))
                .url("http://example.com/plain.txt")
                .build();
        Request request = authenticator.authorizationRequest(original, authScheme);
        String auth = request.header("Authorization");
        assertTrue(auth.contains("username=\"admin\""));
        assertTrue(auth.contains("realm=\"MD5-sess Example\""));
        assertTrue(auth.contains("nonce=\"dcd98b7102dd2f0e8b11d0f600bfb0c093\""));
        assertTrue(auth.contains("uri=\"/plain.txt\""));
        assertTrue(auth.contains("cnonce=\"hxk1lu63b6c7vhk\""));
        assertTrue(auth.contains("nc=00000001"));
        assertTrue(auth.contains("qop=auth"));
        assertTrue(auth.contains("response=\"af2a72145775cfd08c36ad2676e89446\""));
        assertTrue(auth.contains("opaque=\"5ccc069c403ebaf9f0171e9517f40e41\""));
    }

    public void testMD5AuthInt() {
        BasicDigestAuthenticator authenticator = new BasicDigestAuthenticator(null, "admin", "12435", "hxk1lu63b6c7vhk");

        HttpUtils.AuthScheme authScheme = new HttpUtils.AuthScheme("Digest");
        authScheme.params.put("realm", "AuthInt Example");
        authScheme.params.put("qop", "auth-int");
        authScheme.params.put("nonce", "367sj3265s5");
        authScheme.params.put("opaque", "87aaxcval4gba36");

        /*  A1 = admin:AuthInt Example:12345                            380dc3fc1305127cd2aa81ab68ef3f34

            h("PLAIN TEXT") = 20296edbd4c4275fb416b64e4be752f9
            A2 = POST:/plain.txt:20296edbd4c4275fb416b64e4be752f9       a71c4c86e18b3993ffc98c6e426fe4b0

            h(380dc3fc1305127cd2aa81ab68ef3f34:367sj3265s5:00000001:hxk1lu63b6c7vhk:auth-int:a71c4c86e18b3993ffc98c6e426fe4b0) =
            81d07cb3b8d412b34144164124c970cb
        */

        Request original = new Request.Builder()
                .method("POST", RequestBody.create(MediaType.parse("text/plain"), "PLAIN TEXT"))
                .url("http://example.com/plain.txt")
                .build();
        Request request = authenticator.authorizationRequest(original, authScheme);
        String auth = request.header("Authorization");
        assertTrue(auth.contains("username=\"admin\""));
        assertTrue(auth.contains("realm=\"AuthInt Example\""));
        assertTrue(auth.contains("nonce=\"367sj3265s5\""));
        assertTrue(auth.contains("uri=\"/plain.txt\""));
        assertTrue(auth.contains("cnonce=\"hxk1lu63b6c7vhk\""));
        assertTrue(auth.contains("nc=00000001"));
        assertTrue(auth.contains("qop=auth-int"));
        assertTrue(auth.contains("response=\"5ab6822b9d906cc711760a7783b28dca\""));
        assertTrue(auth.contains("opaque=\"87aaxcval4gba36\""));
    }

    public void testLegacyDigest() {
        BasicDigestAuthenticator authenticator = new BasicDigestAuthenticator(null, "Mufasa", "CircleOfLife");

        // construct WWW-Authenticate
        HttpUtils.AuthScheme authScheme = new HttpUtils.AuthScheme("Digest");
        authScheme.params.put("realm", "testrealm@host.com");
        authScheme.params.put("nonce", "dcd98b7102dd2f0e8b11d0f600bfb0c093");
        authScheme.params.put("opaque", "5ccc069c403ebaf9f0171e9517f40e41");

        Request original = new Request.Builder()
                .get()
                .url("http://www.nowhere.org/dir/index.html")
                .build();
        Request request = authenticator.authorizationRequest(original, authScheme);
        String auth = request.header("Authorization");
        assertTrue(auth.contains("username=\"Mufasa\""));
        assertTrue(auth.contains("realm=\"testrealm@host.com\""));
        assertTrue(auth.contains("nonce=\"dcd98b7102dd2f0e8b11d0f600bfb0c093\""));
        assertTrue(auth.contains("uri=\"/dir/index.html\""));
        assertFalse(auth.contains("qop="));
        assertFalse(auth.contains("nc="));
        assertFalse(auth.contains("cnonce="));
        assertTrue(auth.contains("response=\"1949323746fe6a43ef61f9606e7febea\""));
        assertTrue(auth.contains("opaque=\"5ccc069c403ebaf9f0171e9517f40e41\""));
    }

    public void testIncompleteAuthenticationRequests() {
        BasicDigestAuthenticator authenticator = new BasicDigestAuthenticator(null, "demo", "demo");

        Request original = new Request.Builder()
                .get()
                .url("http://www.nowhere.org/dir/index.html")
                .build();

        HttpUtils.AuthScheme authScheme = new HttpUtils.AuthScheme("Digest");
        assertNull(authenticator.authorizationRequest(original, authScheme));

        authScheme.params.put("realm", "Group-Office");
        assertNull(authenticator.authorizationRequest(original, authScheme));

        authScheme.params.put("qop", "auth");
        assertNull(authenticator.authorizationRequest(original, authScheme));

        authScheme.params.put("nonce", "56212407212c8");
        assertNull(authenticator.authorizationRequest(original, authScheme));

        authScheme.params.put("opaque", "df58bdff8cf60599c939187d0b5c54de");
        assertNotNull(authenticator.authorizationRequest(original, authScheme));
    }

}
