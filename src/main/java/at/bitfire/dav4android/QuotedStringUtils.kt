/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.dav4android;

class QuotedStringUtils {
    companion object {

        @JvmStatic
        fun asQuotedString(raw: String?): String? {
            return if (raw == null)
                null
            else
                "\"" + raw.replace("\\" ,"\\\\").replace("\"", "\\\"") + "\""
        }

        @JvmStatic
        fun decodeQuotedString(quoted: String?): String? {
            if (quoted == null)
                return null

            /*  quoted-string  = ( <"> *(qdtext | quoted-pair ) <"> )
                qdtext         = <any TEXT except <">>
                quoted-pair    = "\" CHAR
            */

            val len = quoted.length
            if (len >= 2 && quoted[0] == '"' && quoted[len-1] == '"') {
                val result = StringBuffer(len)
                var pos = 1
                while (pos < len-1) {
                    var c = quoted[pos]
                    if (c == '\\' && pos != len-2)
                        c = quoted[++pos]
                    result.append(c)
                    pos++
                }
                return result.toString()
            } else
                return quoted
        }

    }
}
