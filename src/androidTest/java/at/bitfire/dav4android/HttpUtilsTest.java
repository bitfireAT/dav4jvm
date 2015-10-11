/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.dav4android;

import android.util.Log;

import com.squareup.okhttp.HttpUrl;

import junit.framework.TestCase;

import java.util.List;

public class HttpUtilsTest extends TestCase {

    public void testParseWwwAuthenticate() {
        List<HttpUtils.AuthScheme> schemes = HttpUtils.parseWwwAuthenticate(new String[]{ "Basic realm=\"test\"" });
        assertEquals(1, schemes.size());
        HttpUtils.AuthScheme scheme = schemes.get(0);
        assertEquals("Basic", scheme.scheme);
        assertEquals(1, scheme.params.size());
        assertEquals("realm=\"test\"", scheme.params.get(0));

        schemes = HttpUtils.parseWwwAuthenticate(new String[]{ "  UnknownWithoutParam,   Unknown   WithParam1,   Param2  " });
        assertEquals(2, schemes.size());
        assertEquals("UnknownWithoutParam", schemes.get(0).scheme);
        assertEquals(0, schemes.get(0).params.size());
        assertEquals("Unknown", schemes.get(1).scheme);
        assertEquals(2, schemes.get(1).params.size());
        assertEquals("WithParam1", schemes.get(1).params.get(0));
        assertEquals("Param2", schemes.get(1).params.get(1));

        // TODO test parameters with quoted strings with commas:
        // X-MyScheme param1, param2="a,b,c", MyOtherScheme paramA
    }

}
