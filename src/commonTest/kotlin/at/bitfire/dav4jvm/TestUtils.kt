package at.bitfire.dav4jvm

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.util.date.*
import io.ktor.utils.io.*
import korlibs.time.DateTime

val HttpClient.lastMockRequest
    get() = (engine as MockEngine).requestHistory.last()

val HttpClient.lastMockResponse
    get() = (engine as MockEngine).responseHistory.last()

fun createMockClient(
    handler: MockRequestHandler = {
        respondError(HttpStatusCode.InternalServerError)
    }
) = HttpClient(MockEngine(handler)) {
    followRedirects = false
}

fun HttpClient.changeMockHandler(handler: MockRequestHandler) {
    if (engine !is MockEngine) error("Only possible with MockEngine")
    val config = (engine as MockEngine).config
    config.requestHandlers.clear()
    config.addHandler(handler)
}

fun Url.resolve(path: String) = URLBuilder(this).apply {
    takeFrom(path)
}.build()

@OptIn(InternalAPI::class)
suspend fun HttpClient.createResponse(
    request: HttpRequestBuilder,
    status: HttpStatusCode,
    headers: Headers = headersOf(),
    body: String = ""
) = HttpClientCall(
    this,
    request.build(),
    HttpResponseData(
        statusCode = status,
        requestTime = GMTDate(DateTime.nowUnixMillisLong()),
        headers = headers,
        version = HttpProtocolVersion.HTTP_1_1,
        body = ByteReadChannel(body),
        callContext = coroutineContext,
    )
).save().response

fun buildRequest(block: HttpRequestBuilder.() -> Unit) = HttpRequestBuilder().apply(block)