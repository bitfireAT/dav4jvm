package at.bitfire.dav4jvm.property.push

import at.bitfire.dav4jvm.Property

/**
 * Represents a `{DAV:Push}server-public-key` property.
 *
 * Experimental! See https://github.com/bitfireAT/webdav-push/
 */
class ServerPublicKey: PushPublicKey() {
    companion object {
        @JvmField
        val NAME = Property.Name(NS_WEBDAV_PUSH, "server-public-key")
    }

    object Factory : PushPublicKey.Factory<ServerPublicKey>(NAME, ::ServerPublicKey)
}
