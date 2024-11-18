package at.bitfire.dav4jvm.property.push

import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.PropertyFactory
import org.xmlpull.v1.XmlPullParser

/**
 * Represents a `{DAV:Push}client-public-key` property.
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
