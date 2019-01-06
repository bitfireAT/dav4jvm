/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm

import okhttp3.HttpUrl
import okhttp3.Response
import java.util.*
import java.util.regex.Pattern

object HttpUtils {

    private val authSchemeWithParam = Pattern.compile("^([^ \"]+) +(.*)$")

    /**
     * Gets the resource name (the last segment of the path) from an URL.
     *
     * @return resource name or `` (empty string) if the URL ends with a slash
     *         (i.e. the resource is a collection).
     */
    fun fileName(url: HttpUrl): String {
        val pathSegments = url.pathSegments()
        return pathSegments[pathSegments.size - 1]
    }

    fun listHeader(response: Response, name: String): Array<String> {
        val value = response.headers(name).joinToString(",")
        return value.split(',').filter { it.isNotEmpty() }.toTypedArray()
    }

    @Suppress("DEPRECATION")
    @Deprecated("Use okhttp Challenge API")
    fun parseWwwAuthenticate(wwwAuths: List<String>): List<AuthScheme> {
        /* WWW-Authenticate  = "WWW-Authenticate" ":" 1#challenge

           challenge      = auth-scheme 1*SP 1#auth-param
           auth-scheme    = token
           auth-param     = token "=" ( token | quoted-string )

           We call the auth-param tokens: <name>=<value>

           token          = 1*<any CHAR except CTLs or separators>
           separators     = "(" | ")" | "<" | ">" | "@"
                          | "," | ";" | ":" | "\" | <">
                          | "/" | "[" | "]" | "?" | "="
                          | "{" | "}" | SP | HT

           quoted-string  = ( <"> *(qdtext | quoted-pair ) <"> )
           qdtext         = <any TEXT except <">>
           quoted-pair    = "\" CHAR
        */

        val schemes = LinkedList<AuthScheme>()
        for (wwwAuth in wwwAuths) {
            // Step 1: tokenize by ',', but take into account that auth-param values may contain quoted-pair values with ',' in it (these ',' have to be ignored)
            // Auth-scheme and auth-param names are tokens and thus must not contain the '"' separator.
            val tokens = LinkedList<String>()
            var token = StringBuilder()

            var inQuotes = false
            val len = wwwAuth.length
            var i = 0
            while (i < len) {
                var c = wwwAuth[i]

                var literal = false
                if (c == '"')
                    inQuotes = !inQuotes
                else if (inQuotes && c == '\\' && i + 1 < len) {
                    token.append(c)

                    c = wwwAuth[++i]
                    literal = true
                }

                if (c == ',' && !inQuotes && !literal) {
                    tokens.add(token.toString())
                    token = StringBuilder()
                } else
                    token.append(c)

                i++
            }
            if (token.isNotEmpty())
                tokens.add(token.toString())

            /* Step 2: determine token type after trimming:
                    "<authSchemes> <auth-param>"        new auth scheme + 1 param
                    "<auth-param>"                      add param to previous auth scheme
                    Take into account that the second type may contain quoted spaces.
                    The auth scheme name must not contain separators (including quotes).
                 */
            var scheme: AuthScheme? = null
            for (s in tokens) {
                @Suppress("NAME_SHADOWING")
                val s: String = s.trim()

                val matcher = authSchemeWithParam.matcher(s)
                when {
                    matcher.matches() -> {
                        // auth-scheme with auth-param
                        scheme = AuthScheme(matcher.group(1))
                        schemes.add(scheme)
                        scheme.addRawParam(matcher.group(2))
                    }
                    scheme != null ->
                        // if there was an auth-scheme before, this must be an auth-param
                        scheme.addRawParam(s)
                    else -> {
                        // there was not auth-scheme before, so this must be an auth-scheme
                        scheme = AuthScheme(s)
                        schemes.add(scheme)
                    }
                }
            }
        }

        Constants.log.finer("Server authentication schemes: ")
        for (scheme in schemes)
            Constants.log.finer("  - $scheme")

        return schemes
    }


    @Deprecated("Use okhttp Challenge API")
    class AuthScheme(
            val name: String
    ) {
        private val nameValue = Pattern.compile("^([^=]+)=(.*)$")!!

        /** Map (name -> value) authentication parameters. Names are always lower-case. */
        val params = mutableMapOf<String, String>()
        val unnamedParams = LinkedList<String>()

        fun addRawParam(authParam: String) {
            val m = nameValue.matcher(authParam)
            if (m.matches()) {
                val name = m.group(1)
                var value = m.group(2)
                val len = value.length
                if (value[0] == '"' && value[len - 1] == '"')
                    // quoted-string
                    value = value
                            .substring(1, len - 1)
                            .replace("\\\"", "\"")
                params[name.toLowerCase()] = value
            } else
                unnamedParams.add(authParam)
        }

        override fun toString(): String {
            val s = StringBuilder()
            s.append("$name(")
            for ((name, value) in params)
                s.append("$name=[$value],")
            s.append(")")
            return s.toString()
        }

    }

}