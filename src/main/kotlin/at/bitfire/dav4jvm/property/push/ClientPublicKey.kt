package at.bitfire.dav4jvm.property.push

import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.PropertyFactory
import org.xmlpull.v1.XmlPullParser

/**
 * Represents a `{DAV:Push}client-public-key` property.
 *
 * Experimental! See https://github.com/bitfireAT/webdav-push/
 */
data class ClientPublicKey(
    val type: String?,
    val value: String?
): Property {

    companion object {
        @JvmField
        val NAME = Property.Name(NS_WEBDAV_PUSH, "client-public-key")

        const val PROP_TYPE = "type"
    }

    object Factory: PropertyFactory {

        override fun getName() = PushTransports.NAME

        override fun create(parser: XmlPullParser): ClientPublicKey {
            val type = parser.getAttributeValue(NS_WEBDAV_PUSH, PROP_TYPE)
            val value = parser.text

            return ClientPublicKey(type, value)
        }

    }

}
