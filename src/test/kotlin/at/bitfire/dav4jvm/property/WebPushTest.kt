package at.bitfire.dav4jvm.property

import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.property.push.NS_WEBDAV_PUSH
import at.bitfire.dav4jvm.property.push.PushSubscribe
import at.bitfire.dav4jvm.property.push.PushTransports
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class WebPushTest: PropertyTest() {

    @Test
    fun testPushSubscribe() {
        val results = parseProperty(
            "<push-subscribe xmlns=\"DAV:Push\">" +
            "  <subscription>" +
            "    <web-push-subscription>\n" +
            "      <push-resource>https://up.example.net/yohd4yai5Phiz1wi</push-resource>\n" +
            "    </web-push-subscription>\n" +
            "  </subscription>" +
            "  <expires>Wed, 20 Dec 2023 10:03:31 GMT</expires>" +
            "</push-subscribe>")
        val result = results.first() as PushSubscribe
        assertEquals(Instant.ofEpochSecond(1703066611), result.expires)
        assertEquals("https://up.example.net/yohd4yai5Phiz1wi", result.subscription?.webPushSubscription?.pushResource)
    }

    @Test
    fun testPushTransports() {
        val results = parseProperty(
                "<push-transports xmlns=\"DAV:Push\">" +
                "  <transport><something><else/></something></transport>" +
                "  <transport><web-push/></transport>" +
                "</push-transports>")
        val result = results.first() as PushTransports
        assertEquals(setOf(
            Property.Name(NS_WEBDAV_PUSH, "something"),
            PushTransports.WEB_PUSH
        ), result.transports)
        assertTrue(result.hasWebPush())
    }

}