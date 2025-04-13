package at.bitfire.dav4jvm.property.webdav

import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.PropertyFactory
import at.bitfire.dav4jvm.XmlReader
import org.xmlpull.v1.XmlPullParser

/**
 * Represents a [NS_WEBDAV]`:sync-level` property.
 */
data class SyncLevel(
    /** May be `0`, `1` or [Int.MAX_VALUE] (infinite). */
    val level: Int? = null
): Property {

    companion object {

        @JvmField
        val NAME = Property.Name(NS_WEBDAV, "sync-level")

    }


    object Factory: PropertyFactory {

        override fun getName() = NAME

        override fun create(parser: XmlPullParser): SyncLevel {
            val text = XmlReader(parser).readText()
            val level = if (text == "infinite") Int.MAX_VALUE else text?.toIntOrNull()
            return SyncLevel(level)
        }

    }

}
