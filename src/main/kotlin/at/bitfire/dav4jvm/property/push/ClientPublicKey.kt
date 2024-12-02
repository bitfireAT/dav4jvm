package at.bitfire.dav4jvm.property.push

import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.PropertyFactory
import at.bitfire.dav4jvm.XmlReader
import org.xmlpull.v1.XmlPullParser

/**
 * Represents a [NS_WEBDAV_PUSH]`:client-public-key` property.
 *
 * Experimental! See https://github.com/bitfireAT/webdav-push/
 */
class ClientPublicKey: PushPublicKey() {

    companion object {

        @JvmField
        val NAME = Property.Name(NS_WEBDAV_PUSH, "client-public-key")

    }


    object Factory : PropertyFactory {

        override fun getName() = NAME

        override fun create(parser: XmlPullParser): ClientPublicKey {
            val publicKey = ClientPublicKey()

            publicKey.type = parser.getAttributeValue(null, "type")
            publicKey.key = XmlReader(parser).readText()

            return publicKey
        }

    }

}
