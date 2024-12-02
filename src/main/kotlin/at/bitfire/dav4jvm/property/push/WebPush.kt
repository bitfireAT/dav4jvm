package at.bitfire.dav4jvm.property.push

import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.PropertyFactory
import org.xmlpull.v1.XmlPullParser

/**
 * Represents a [NS_WEBDAV_PUSH]`:web-push` property.
 *
 * Experimental! See https://github.com/bitfireAT/webdav-push/
 */
data class WebPush(
    val serverPublicKey: ServerPublicKey? = null
): PushTransport {

    companion object {
        @JvmField
        val NAME = Property.Name(NS_WEBDAV_PUSH, "web-push")
    }


    object Factory: PropertyFactory {

        override fun getName(): Property.Name = NAME

        override fun create(parser: XmlPullParser): WebPush {
            var serverPublicKey: ServerPublicKey? = null
            val depth = parser.depth
            var eventType = parser.eventType
            while (!(eventType == XmlPullParser.END_TAG && parser.depth == depth)) {
                if (eventType == XmlPullParser.START_TAG && parser.namespace == NS_WEBDAV_PUSH) {
                    when (parser.name) {
                        ServerPublicKey.NAME.name
                             -> serverPublicKey = ServerPublicKey.Factory.create(parser)
                    }
                }
                eventType = parser.next()
            }
            return WebPush(serverPublicKey)
        }

    }

}
