package at.bitfire.dav4jvm.property

import io.ktor.client.plugins.auth.*
import io.ktor.client.request.*
import io.ktor.http.auth.*

fun Auth.forDomain(pattern: String, block: Auth.() -> Unit) = forDomain(pattern.toRegex(), block)

fun Auth.forDomain(pattern: Regex, block: Auth.() -> Unit) {
    val old = this.providers.toSet()
    block()
    val newProviders = providers - old
    providers.removeAll(newProviders)
    newProviders.forEach { providers += AuthDomainLimiter(pattern, it) }
}

class AuthDomainLimiter(private val domain: Regex, private val downstreamProvider: AuthProvider) : AuthProvider {
    @Deprecated("Please use sendWithoutRequest function instead")
    override val sendWithoutRequest: Boolean
        get() = error("Deprecated")

    override fun sendWithoutRequest(request: HttpRequestBuilder): Boolean =
        domain.matches(request.url.buildString()) && downstreamProvider.sendWithoutRequest(request)

    override suspend fun addRequestHeaders(request: HttpRequestBuilder, authHeader: HttpAuthHeader?) {
        if (domain.matches(request.url.buildString())) downstreamProvider.addRequestHeaders(request, authHeader)
    }

    override fun isApplicable(auth: HttpAuthHeader): Boolean = downstreamProvider.isApplicable(auth)

}
