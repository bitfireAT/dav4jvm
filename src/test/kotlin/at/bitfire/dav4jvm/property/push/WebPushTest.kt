package at.bitfire.dav4jvm.property.push

import at.bitfire.dav4jvm.property.PropertyTest
import at.bitfire.dav4jvm.property.webdav.SyncToken
import java.time.Instant
import okhttp3.Protocol
import okhttp3.internal.http.StatusLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebPushTest: PropertyTest() {

    @Test
    fun testPushRegister() {
        val results = parseProperty(
            "<push-register xmlns=\"$NS_WEBDAV_PUSH\">" +
            "  <subscription>" +
            "    <web-push-subscription>\n" +
            "      <push-resource>https://up.example.net/yohd4yai5Phiz1wi</push-resource>\n" +
            "      <client-public-key type=\"p256dh\">BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4</client-public-key>\n" +
            "      <auth-secret>BTBZMqHH6r4Tts7J_aSIgg</auth-secret>" +
            "    </web-push-subscription>\n" +
            "  </subscription>" +
            "  <expires>Wed, 20 Dec 2023 10:03:31 GMT</expires>" +
            "</push-register>")
        val result = results.first() as PushRegister
        assertEquals(Instant.ofEpochSecond(1703066611), result.expires)
        val subscription = result.subscription?.webPushSubscription
        assertEquals("https://up.example.net/yohd4yai5Phiz1wi", subscription?.pushResource?.uri?.toString())
        assertEquals("BTBZMqHH6r4Tts7J_aSIgg", subscription?.authSecret?.secret)

        val publicKey = subscription?.clientPublicKey
        assertEquals("p256dh", publicKey?.type)
        assertEquals("BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4", publicKey?.key)
    }

    @Test
    fun testServiceDetection() {
        val results = parseProperty(
                "<transports xmlns=\"$NS_WEBDAV_PUSH\">" +
                "  <something><else/></something>" +
                "  <web-push>" +
                "    <server-public-key type=\"p256dh\">BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4</server-public-key>" +
                "  </web-push>" +
                "</transports>" +
                "<topic xmlns=\"$NS_WEBDAV_PUSH\">SomeTopic</topic>")
        val result = results.first() as PushTransports

        assertEquals(setOf(
            // something else is ignored because it's not a recognized transport
            WebPush(
                ServerPublicKey().apply {
                    type = "p256dh"
                    key = "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4"
                }
            )
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