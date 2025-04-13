package at.bitfire.dav4jvm.property.push

import at.bitfire.dav4jvm.property.PropertyTest
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
            "      <subscription-public-key type=\"p256dh\">BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4</subscription-public-key>\n" +
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

        val publicKey = subscription?.subscriptionPublicKey
        assertEquals("p256dh", publicKey?.type)
        assertEquals("BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4", publicKey?.key)
    }

    @Test
    fun testServiceDetection() {
        val results = parseProperty(
                "<transports xmlns=\"$NS_WEBDAV_PUSH\">" +
                "  <something><else/></something>" +
                "  <web-push>" +
                "    <vapid-public-key type=\"p256dh\">BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4</vapid-public-key>" +
                "  </web-push>" +
                "</transports>" +
                "<topic xmlns=\"$NS_WEBDAV_PUSH\">SomeTopic</topic>")
        val result = results.first() as PushTransports

        assertEquals(setOf(
            // something else is ignored because it's not a recognized transport
            WebPush(
                VapidPublicKey(
                    type = "p256dh",
                    key = "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4"
                )
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
              <P:topic>O7M1nQ7cKkKTKsoS_j6Z3w</P:topic>
              <P:content-update>
                <D:sync-token>http://example.com/sync/10</D:sync-token>
              </P:content-update>
              <P:property-update />
            </P:push-message>
            """.trimIndent()
        )
        val message = results.first() as PushMessage

        val topic = message.topic
        assertNotNull(topic)
        assertEquals("O7M1nQ7cKkKTKsoS_j6Z3w", topic?.topic)

        val contentUpdate = message.contentUpdate
        assertNotNull(contentUpdate)
        val syncToken = contentUpdate?.syncToken
        assertNotNull(syncToken)
        assertEquals("http://example.com/sync/10", syncToken?.token)

        val propertyUpdate = message.propertyUpdate
        assertNotNull(propertyUpdate)
    }

}