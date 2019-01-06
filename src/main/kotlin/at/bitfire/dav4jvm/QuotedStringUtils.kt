/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm

object QuotedStringUtils {

    fun asQuotedString(raw: String) =
            "\"" + raw.replace("\\" ,"\\\\").replace("\"", "\\\"") + "\""

    fun decodeQuotedString(quoted: String): String {
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