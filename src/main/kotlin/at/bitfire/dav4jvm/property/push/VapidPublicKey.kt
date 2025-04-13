package at.bitfire.dav4jvm.property.push

import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.PropertyFactory
import at.bitfire.dav4jvm.XmlReader
import org.xmlpull.v1.XmlPullParser

/**
 * Represents a [NS_WEBDAV_PUSH]`:vapid-public-key` property.
 *
 * Experimental! See https://github.com/bitfireAT/webdav-push/
 */
data class VapidPublicKey(
    val type: String? = null,
    val key: String? = null
): Property {

    companion object {

        @JvmField
        val NAME = Property.Name(NS_WEBDAV_PUSH, "vapid-public-key")

    }


    object Factory : PropertyFactory {

        override fun getName() = NAME

        override fun create(parser: XmlPullParser): VapidPublicKey {
            return VapidPublicKey(
                type = parser.getAttributeValue(null, "type"),
                key = XmlReader(parser).readText()
            )
        }

    }

}