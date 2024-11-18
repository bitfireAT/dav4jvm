package at.bitfire.dav4jvm.property

import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.property.push.*
import at.bitfire.dav4jvm.property.webdav.SyncToken
import okhttp3.Protocol
import okhttp3.internal.http.StatusLine
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import kotlin.math.exp

class WebPushTest: PropertyTest() {

    @Test
    fun testPushRegister() {
        val results = parseProperty(
            "<push-register xmlns=\"$NS_WEBDAV_PUSH\">" +
            "  <subscription>" +
            "    <web-push-subscription>\n" +
            "      <push-resource>https://up.example.net/yohd4yai5Phiz1wi</push-resource>\n" +
            "    </web-push-subscription>\n" +
            "  </subscription>" +
            "  <expires>Wed, 20 Dec 2023 10:03:31 GMT</expires>" +
            "</push-register>")
        val result = results.first() as PushRegister
        assertEquals(Instant.ofEpochSecond(1703066611), result.expires)
        assertEquals("https://up.example.net/yohd4yai5Phiz1wi", result.subscription?.webPushSubscription?.pushResource)
    }

    @Test
    fun testServiceDetection() {
        val results = parseProperty(
                "<transports xmlns=\"$NS_WEBDAV_PUSH\">" +
                "  <something><else/></something>" +
                "  <web-push/>" +
                "</transports>" +
                "<topic xmlns=\"$NS_WEBDAV_PUSH\">SomeTopic</topic>")
        val result = results.first() as PushTransports

        assertEquals(setOf(
            // something else is ignored because it's not a recognized transport
            WebPush(null)
        ), result.transports)
        assertTrue(result.hasWebPush())

        assertEquals("SomeTopic", (results[1] as Topic).topic)
    }

    @Test
    fun testMessage() {
        val results = parseProperty(
            """
            <P:push-message xmlns:D="DAV:" xmlns:P="$NS_WEBDAV_PUSH">
              <D:propstat>
                <D:prop>
                  <P:topic>O7M1nQ7cKkKTKsoS_j6Z3w</P:topic>
                  <D:sync-token>http://example.com/ns/sync/1234</D:sync-token>
                </D:prop>
              </D:propstat>
            </P:push-message>
            """.trimIndent()
        )
        val message = results.first() as PushMessage

        val propStat = message.propStat
        assertNotNull(propStat)
        // Since the status line is not set, it's assumed to be OK by default
        assertStatusEqual(StatusLine(Protocol.HTTP_1_1, 200, "Assuming OK"), propStat?.status)

        val properties = propStat?.properties
        assertNotNull(properties)
        assertEquals(2, properties?.size)

        val topic = properties!![0] as Topic
        assertEquals("O7M1nQ7cKkKTKsoS_j6Z3w", topic.topic)

        val token = properties[1] as SyncToken
        assertEquals("http://example.com/ns/sync/1234", token.token)
    }

    /**
     * StatusLine doesn't have an `equals` method, so we need to compare its fields manually.
     */
    private fun assertStatusEqual(expected: StatusLine?, actual: StatusLine?) {
        if (expected == null) {
            assertNull(actual)
            return
        }
        assertNotNull(actual)
        assertEquals(expected.protocol, actual?.protocol)
        assertEquals(expected.code, actual?.code)
        assertEquals(expected.message, actual?.message)
    }

}