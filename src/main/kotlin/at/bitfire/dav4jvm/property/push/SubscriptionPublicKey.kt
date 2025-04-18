package at.bitfire.dav4jvm.property.push

import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.PropertyFactory
import at.bitfire.dav4jvm.XmlReader
import org.xmlpull.v1.XmlPullParser

/**
 * Represents a [NS_WEBDAV_PUSH]`:subscription-public-key` property.
 *
 * Experimental! See https://github.com/bitfireAT/webdav-push/
 */
data class SubscriptionPublicKey(
    val type: String? = null,
    val key: String? = null
): Property {

    companion object {

        @JvmField
        val NAME = Property.Name(NS_WEBDAV_PUSH, "subscription-public-key")

    }


    object Factory : PropertyFactory {

        override fun getName() = NAME

        override fun create(parser: XmlPullParser): SubscriptionPublicKey {
            return SubscriptionPublicKey(
                type = parser.getAttributeValue(null, "type"),
                key = XmlReader(parser).readText()
            )
        }

    }

}