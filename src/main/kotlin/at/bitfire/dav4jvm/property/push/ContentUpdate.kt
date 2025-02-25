package at.bitfire.dav4jvm.property.push

import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.PropertyFactory
import at.bitfire.dav4jvm.XmlUtils.propertyName
import at.bitfire.dav4jvm.property.webdav.SyncLevel
import at.bitfire.dav4jvm.property.webdav.SyncToken
import org.xmlpull.v1.XmlPullParser

/**
 * Represents a [NS_WEBDAV_PUSH]`:content-update` property.
 *
 * Experimental! See https://github.com/bitfireAT/webdav-push/
 */
data class ContentUpdate(
    val syncLevel: SyncLevel? = null,
    val syncToken: SyncToken? = null
): Property {

    companion object {

        @JvmField
        val NAME = Property.Name(NS_WEBDAV_PUSH, "content-update")

    }


    object Factory: PropertyFactory {

        override fun getName() = NAME

        override fun create(parser: XmlPullParser): ContentUpdate {
            var contentUpdate = ContentUpdate()

            val depth = parser.depth
            var eventType = parser.eventType
            while (!(eventType == XmlPullParser.END_TAG && parser.depth == depth)) {
                if (eventType == XmlPullParser.START_TAG && parser.depth == depth + 1) {
                    when (parser.propertyName()) {
                        SyncLevel.NAME -> contentUpdate = contentUpdate.copy(
                            syncLevel = SyncLevel.Factory.create(parser)
                        )
                        SyncToken.NAME -> contentUpdate = contentUpdate.copy(
                            syncToken = SyncToken.Factory.create(parser)
                        )
                    }
                }
                eventType = parser.next()
            }

            return contentUpdate
        }

    }

}
