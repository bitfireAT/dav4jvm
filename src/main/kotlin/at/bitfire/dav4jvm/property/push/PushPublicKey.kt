package at.bitfire.dav4jvm.property.push

import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.PropertyFactory
import at.bitfire.dav4jvm.XmlReader
import at.bitfire.dav4jvm.XmlUtils.propertyName
import org.xmlpull.v1.XmlPullParser

/**
 * Represents a public key property from Push.
 *
 * Experimental! See https://github.com/bitfireAT/webdav-push/
 *
 * @see ClientPublicKey
 * @see ServerPublicKey
 */
abstract class PushPublicKey: Property {

    companion object {
        @JvmField
        val NAME = Property.Name(NS_WEBDAV_PUSH, "server-public-key")
    }

    var type: String? = null
    var key: String? = null


    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PushPublicKey) return false

        if (type != other.type) return false
        if (key != other.key) return false

        return true
    }

    override fun hashCode(): Int {
        var result = type?.hashCode() ?: 0
        result = 31 * result + (key?.hashCode() ?: 0)
        return result
    }


    abstract class Factory<KeyType: PushPublicKey>(
        private val name: Property.Name,
        private val constructor: () -> KeyType
    ): PropertyFactory {

        override fun getName() = name

        override fun create(parser: XmlPullParser): KeyType {
            val publicKey = constructor()

            publicKey.type = parser.getAttributeValue(null, "type")
            publicKey.key = XmlReader(parser).readText()

            return publicKey
        }

    }

}
