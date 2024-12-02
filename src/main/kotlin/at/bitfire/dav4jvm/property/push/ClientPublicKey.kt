package at.bitfire.dav4jvm.property.push

import at.bitfire.dav4jvm.Property

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


    object Factory : PushPublicKey.Factory<ClientPublicKey>(NAME, ::ClientPublicKey)
}
