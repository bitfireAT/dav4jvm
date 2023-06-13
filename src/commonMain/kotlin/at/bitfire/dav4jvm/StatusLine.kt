package at.bitfire.dav4jvm

import io.ktor.client.statement.*
import io.ktor.http.*
import kotlin.jvm.JvmField

class StatusLine(
    @JvmField val protocol: HttpProtocolVersion,
    @JvmField val status: HttpStatusCode
) {

    override fun toString(): String {
        return buildString {
            append(protocol.toString())
            append(' ').append(status)
        }
    }

    companion object {
        fun get(response: HttpResponse): StatusLine {
            return StatusLine(response.version, response.status)
        }

        @Throws(IllegalStateException::class)
        fun parse(statusLine: String): StatusLine {
            // H T T P / 1 . 1   2 0 0   T e m p o r a r y   R e d i r e c t
            // 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0

            // Parse protocol like "HTTP/1.1" followed by a space.
            val codeStart: Int = statusLine.indexOf(" ") + 1
            val protocol: HttpProtocolVersion = if (statusLine.startsWith("HTTP/")) {
                HttpProtocolVersion.parse(statusLine.takeWhile { !it.isWhitespace() })
            } else if (statusLine.startsWith("ICY ")) {
                // Shoutcast uses ICY instead of "HTTP/1.0".
                HttpProtocolVersion.HTTP_1_0
            } else if (statusLine.startsWith("SOURCETABLE ")) {
                // NTRIP r1 uses SOURCETABLE instead of HTTP/1.1
                HttpProtocolVersion.HTTP_1_1
            } else {
                throw IllegalStateException("Unexpected status line: $statusLine")
            }

            // Parse response code like "200". Always 3 digits.
            if (statusLine.length < codeStart + 3) {
                throw IllegalStateException("Unexpected status line: $statusLine")
            }
            val code =
                statusLine.substring(codeStart, codeStart + 3).toIntOrNull()
                    ?: throw IllegalStateException(
                        "Unexpected status line: $statusLine"
                    )

            // Parse an optional response message like "OK" or "Not Modified". If it
            // exists, it is separated from the response code by a space.
            var message = ""
            if (statusLine.length > codeStart + 3) {
                if (statusLine[codeStart + 3] != ' ') {
                    throw IllegalStateException("Unexpected status line: $statusLine")
                }
                message = statusLine.substring(codeStart + 4)
            }

            return StatusLine(protocol, HttpStatusCode(code, message))
        }
    }
}