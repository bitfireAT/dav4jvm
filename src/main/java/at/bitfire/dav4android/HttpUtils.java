/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.dav4android;

import android.text.TextUtils;

import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;

import lombok.RequiredArgsConstructor;

public class HttpUtils {

    public static List<AuthScheme> parseWwwAuthenticate(String[] wwwAuths) {
        List<AuthScheme> schemes = new LinkedList<>();
        for (String wwwAuth : wwwAuths) {
            StringTokenizer tok = new StringTokenizer(wwwAuth.trim(), ",");

            AuthScheme scheme = null;
            while (tok.hasMoreTokens()) {
                String token = tok.nextToken().trim();
                Constants.log.debug("Token: " + token);
                if (token.contains(" ")) {
                    String parts[] = token.split(" +");
                    schemes.add(scheme = new AuthScheme(parts[0]));
                    scheme.params.add(parts[1]);
                } else {
                    if (scheme != null)
                        scheme.params.add(token);
                    else
                        schemes.add(scheme = new AuthScheme(token));
                }
            }
        }

        Constants.log.debug("Server authentication schemes: ");
        for (AuthScheme scheme : schemes)
            Constants.log.debug("  - " + scheme);
        return schemes;
    }

    @RequiredArgsConstructor
    public static class AuthScheme {
        public final String scheme;
        public final List<String> params = new LinkedList<>();

        @Override
        public String toString() {
            return scheme + "(" + TextUtils.join(",", params) + ")";
        }
    }
}
