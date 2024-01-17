package at.bitfire.dav4jvm.property

import at.bitfire.dav4jvm.property.push.PushSubscribe
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class WebPushTest: PropertyTest() {

    @Test
    fun testPushSubscribe() {
        val results = parseProperty(
                "<push-subscribe xmlns=\"DAV:Push\">" +
                "  <web-push-subscription>\n" +
                "      <push-resource>https://up.example.net/yohd4yai5Phiz1wi</push-resource>\n" +
                "  </web-push-subscription>\n" +
                "  <expires>Wed, 20 Dec 2023 10:03:31 GMT</expires>" +
                "</push-subscribe>")
        val result = results.first() as PushSubscribe
        assertEquals(Instant.ofEpochSecond(1703066611), result.expires)
        assertEquals("https://up.example.net/yohd4yai5Phiz1wi", result.webPushSubscription?.pushResource)
    }

}