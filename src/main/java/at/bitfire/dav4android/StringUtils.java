/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.dav4android;

public class StringUtils {

    public static String asQuotedString(String raw) {
        if (raw == null)
            return null;
        return "\"" + raw.replace("\\" ,"\\\\").replace("\"", "\\\"") + "\"";
    }

    public static String decodeQuotedString(String quoted) {
        if (quoted == null)
            return null;

        /*  quoted-string  = ( <"> *(qdtext | quoted-pair ) <"> )
            qdtext         = <any TEXT except <">>
            quoted-pair    = "\" CHAR
        */

        int len = quoted.length();
        if (len >= 2 && quoted.charAt(0) == '"' && quoted.charAt(len-1) == '"') {
            StringBuffer result = new StringBuffer(len);
            //quoted = quoted.substring(1, len-1);
            for (int pos = 1; pos < len-1; pos++) {
                char c = quoted.charAt(pos);
                if (c == '\\' && pos != len-2)
                    c = quoted.charAt(++pos);
                result.append(c);
            }
            return result.toString();
        } else
            return quoted;
    }

}
