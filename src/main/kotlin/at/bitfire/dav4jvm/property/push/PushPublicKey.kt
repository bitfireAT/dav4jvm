package at.bitfire.dav4jvm.property.push

import at.bitfire.dav4jvm.Property

/**
 * Represents a public key property from Push.
 *
 * Experimental! See https://github.com/bitfireAT/webdav-push/
 *
 * @see ClientPublicKey
 * @see ServerPublicKey
 */
abstract class PushPublicKey: Property {

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

}
