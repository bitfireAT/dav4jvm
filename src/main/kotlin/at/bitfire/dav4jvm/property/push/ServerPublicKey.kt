package at.bitfire.dav4jvm.property.push

import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.PropertyFactory
import org.xmlpull.v1.XmlPullParser

/**
 * Represents a `{DAV:Push}server-public-key` property.
 *
 * Experimental! See https://github.com/bitfireAT/webdav-push/
 */
data class ServerPublicKey(
    val type: String?,
    val value: String?
): Property {

    companion object {
        @JvmField
        val NAME = Property.Name(NS_WEBDAV_PUSH, "server-public-key")

        const val PROP_TYPE = "type"
    }

    object Factory: PropertyFactory {

        override fun getName() = PushTransports.NAME

        override fun create(parser: XmlPullParser): ServerPublicKey {
            val type = parser.getAttributeValue(NS_WEBDAV_PUSH, PROP_TYPE)
            val value = parser.text

            return ServerPublicKey(type, value)
        }

    }

}
