/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm

import okhttp3.*
import okhttp3.Response
import okio.Buffer
import okio.ByteString
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.util.*
import java.util.concurrent.atomic.AtomicInteger

/**
 * Handler to manage authentication against a given service (may be limited to one domain).
 * There's no domain-based cache, because the same user name and password will be used for
 * all requests.
 *
 * Authentication methods/credentials found to be working will be cached for further requests
 * (this is why the interceptor is needed).
 *
 * Usage: Set as authenticator *and* as network interceptor.
 */
class BasicDigestAuthHandler(
        /** Authenticate only against hosts ending with this domain (may be null, which means no restriction) */
        val domain: String?,

        val username: String,
        val password: String
): Authenticator, Interceptor {

    companion object {
        private const val HEADER_AUTHORIZATION = "Authorization"

        // cached digest parameters
        var clientNonce = h(UUID.randomUUID().toString())
        var nonceCount = AtomicInteger(1)

        fun quotedString(s: String) = "\"" + s.replace("\"", "\\\"") + "\""
        fun h(data: String) = ByteString.of(ByteBuffer.wrap(data.toByteArray())).md5().hex()!!

        fun h(body: RequestBody): String {
            val buffer = Buffer()
            body.writeTo(buffer)
            return ByteString.of(ByteBuffer.wrap(buffer.readByteArray())).md5().hex()
        }

        fun kd(secret: String, data: String) = h("$secret:$data")
    }

    // cached authentication schemes
    private var basicAuth: Challenge? = null
    private var digestAuth: Challenge? = null


    fun authenticateRequest(request: Request, response: Response?): Request? {
        domain?.let {
            val host = request.url().host()
            if (!domain.equals(UrlUtils.hostToDomain(host), true)) {
                Constants.log.warning("Not authenticating against $host because it doesn't belong to $domain")
                return null
            }
        }

        if (response == null) {
            // we're not processing a 401 response

            if (basicAuth == null && digestAuth == null && request.isHttps) {
                Constants.log.fine("Trying Basic auth preemptively")
                basicAuth = Challenge("Basic", "")
            }

        } else {
            // we're processing a 401 response

            var newBasicAuth: Challenge? = null
            var newDigestAuth: Challenge? = null
            for (challenge in response.challenges())
                when {
                    "Basic".equals(challenge.scheme(), true) -> {
                        basicAuth?.let {
                            Constants.log.warning("Basic credentials didn't work last time -> aborting")
                            basicAuth = null
                            return null
                        }
                        newBasicAuth = challenge
                    }
                    "Digest".equals(challenge.scheme(), true) -> {
                        if (digestAuth != null && !"true".equals(challenge.authParams()["stale"], true)) {
                            Constants.log.warning("Digest credentials didn't work last time and server nonce has not expired -> aborting")
                            digestAuth = null
                            return null
                        }
                        newDigestAuth = challenge
                    }
                }

            basicAuth = newBasicAuth
            digestAuth = newDigestAuth
        }

        // we MUST prefer Digest auth [https://tools.ietf.org/html/rfc2617#section-4.6]
        when {
            digestAuth != null -> {
                Constants.log.fine("Adding Digest authorization request for ${request.url()}")
                return digestRequest(request, digestAuth)
            }

            basicAuth != null -> {
                Constants.log.fine("Adding Basic authorization header for ${request.url()}")

                /* In RFC 2617 (obsolete), there was no encoding for credentials defined, although
                 one can interpret it as "use ISO-8859-1 encoding". This has been clarified by RFC 7617,
                 which creates a new charset parameter for WWW-Authenticate, which always must be UTF-8.
                 So, UTF-8 encoding for credentials is compatible with all RFC 7617 servers and many,
                 but not all pre-RFC 7617 servers. */
                return request.newBuilder()
                        .header(HEADER_AUTHORIZATION, Credentials.basic(username, password, Charset.forName("UTF-8")))
                        .build()
            }

            response != null ->
                Constants.log.warning("No supported authentication scheme")
        }

        return null
    }

    fun digestRequest(request: Request, digest: Challenge?): Request? {
        if (digest == null)
            return null

        val realm = digest.authParams()["realm"]
        val opaque = digest.authParams()["opaque"]
        val nonce = digest.authParams()["nonce"]

        val algorithm = Algorithm.determine(digest.authParams()["algorithm"])
        val qop = Protection.selectFrom(digest.authParams()["qop"])

        // build response parameters
        var response: String? = null

        val params = LinkedList<String>()
        params.add("username=${quotedString(username)}")
        if (realm != null)
            params.add("realm=${quotedString(realm)}")
        else {
            Constants.log.warning("No realm provided, aborting Digest auth")
            return null
        }
        if (nonce != null)
            params.add("nonce=${quotedString(nonce)}")
        else {
            Constants.log.warning("No nonce provided, aborting Digest auth")
            return null
        }
        if (opaque != null)
            params.add("opaque=${quotedString(opaque)}")

        if (algorithm != null)
            params.add("algorithm=${quotedString(algorithm.algorithm)}")

        val method = request.method()
        val digestURI = request.url().encodedPath()
        params.add("uri=${quotedString(digestURI)}")

        if (qop != null) {
            params.add("qop=${qop.qop}")
            params.add("cnonce=${quotedString(clientNonce)}")

            val nc = nonceCount.getAndIncrement()
            val ncValue = String.format("%08x", nc)
            params.add("nc=$ncValue")

            val a1: String? = when (algorithm) {
                Algorithm.MD5 ->
                    "$username:$realm:$password"
                Algorithm.MD5_SESSION ->
                    h("$username:$realm:$password") + ":$nonce:$clientNonce"
                else ->
                    null
            }
            Constants.log.finer("A1=$a1")

            val a2: String? = when (qop) {
                Protection.Auth ->
                    "$method:$digestURI"
                Protection.AuthInt -> {
                    try {
                        val body = request.body()
                        "$method:$digestURI:" + (if (body != null) h(body) else h(""))
                    } catch(e: IOException) {
                        Constants.log.warning("Couldn't get entity-body for hash calculation")
                        null
                    }
                }
            }
            Constants.log.finer("A2=$a2")

            if (a1 != null && a2 != null)
                response = kd(h(a1), "$nonce:$ncValue:$clientNonce:${qop.qop}:${h(a2)}")

        } else {
            Constants.log.finer("Using legacy Digest auth")

            // legacy (backwards compatibility with RFC 2069)
            if (algorithm == Algorithm.MD5) {
                val a1 = "$username:$realm:$password"
                val a2 = "$method:$digestURI"
                response = kd(h(a1), nonce + ":" + h(a2))
            }
        }

        return if (response != null) {
            params.add("response=" + quotedString(response))
            request.newBuilder()
                    .header(HEADER_AUTHORIZATION, "Digest " + params.joinToString(", "))
                    .build()
        } else
            null
    }


    private enum class Algorithm(
            val algorithm: String
    ) {
        MD5("MD5"),
        MD5_SESSION("MD5-sess");

        companion object {
            fun determine(paramValue: String?): Algorithm? {
                return when {
                    paramValue == null || Algorithm.MD5.algorithm.equals(paramValue, true) ->
                        Algorithm.MD5
                    Algorithm.MD5_SESSION.algorithm.equals(paramValue, true) ->
                        Algorithm.MD5_SESSION
                    else -> {
                        Constants.log.warning("Ignoring unknown hash algorithm: $paramValue")
                        null
                    }
                }
            }
        }
    }

    private enum class Protection(
        val qop: String
    ) {    // quality of protection:
        Auth("auth"),              // authentication only
        AuthInt("auth-int");       // authentication with integrity protection

        companion object {
            fun selectFrom(paramValue: String?): Protection? {
                paramValue?.let {
                    var qopAuth = false
                    var qopAuthInt = false
                    for (qop in paramValue.split(","))
                        when (qop) {
                            "auth" -> qopAuth = true
                            "auth-int" -> qopAuthInt = true
                        }

                    // prefer auth-int as it provides more protection
                    if (qopAuthInt)
                        return AuthInt
                    else if (qopAuth)
                        return Auth
                }
                return null
            }
        }
    }


    override fun authenticate(route: Route?, response: Response) =
            authenticateRequest(response.request(), response)

    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        if (request.header(HEADER_AUTHORIZATION) == null) {
            // try to apply cached authentication
            val authRequest = authenticateRequest(request, null)
            if (authRequest != null)
                request = authRequest
        }
        return chain.proceed(request)
    }

}