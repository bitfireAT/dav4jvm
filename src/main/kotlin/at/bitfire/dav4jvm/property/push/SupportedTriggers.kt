package at.bitfire.dav4jvm.property.push

import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.PropertyFactory
import at.bitfire.dav4jvm.XmlUtils.propertyName
import org.xmlpull.v1.XmlPullParser

/**
 * Represents a [NS_WEBDAV_PUSH]`:content-update` property.
 *
 * Experimental! See https://github.com/bitfireAT/webdav-push/
 */
data class SupportedTriggers(
    val contentUpdate: ContentUpdate? = null,
    val propertyUpdate: PropertyUpdate? = null
): Property {

    companion object {

        @JvmField
        val NAME = Property.Name(NS_WEBDAV_PUSH, "supported-triggers")

    }


    object Factory: PropertyFactory {

        override fun getName() = NAME

        override fun create(parser: XmlPullParser): SupportedTriggers {
            var supportedTriggers = SupportedTriggers()

            val depth = parser.depth
            var eventType = parser.eventType
            while (!(eventType == XmlPullParser.END_TAG && parser.depth == depth)) {
                if (eventType == XmlPullParser.START_TAG && parser.depth == depth + 1) {
                    when (parser.propertyName()) {
                        ContentUpdate.NAME -> supportedTriggers = supportedTriggers.copy(
                            contentUpdate = ContentUpdate.Factory.create(parser)
                        )
                        PropertyUpdate.NAME -> supportedTriggers = supportedTriggers.copy(
                            propertyUpdate = PropertyUpdate.Factory.create(parser)
                        )
                    }
                }
                eventType = parser.next()
            }

            return supportedTriggers
        }

    }

}
