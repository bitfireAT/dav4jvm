package at.bitfire.dav4jvm.property.webdav

import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.PropertyFactory
import at.bitfire.dav4jvm.XmlReader
import org.xmlpull.v1.XmlPullParser

/**
 * Represents a [NS_WEBDAV]`:depth` property.
 */
data class Depth(
    /** May be `0`, `1` or [Int.MAX_VALUE] (infinite). */
    val depth: Int? = null
): Property {

    companion object {

        @JvmField
        val NAME = Property.Name(NS_WEBDAV, "depth")

        const val INFINITY = Int.MAX_VALUE

    }


    object Factory: PropertyFactory {

        override fun getName() = NAME

        override fun create(parser: XmlPullParser): Depth {
            val text = XmlReader(parser).readText()
            val level = if (text.equals("infinity", true))
                INFINITY
            else
                text?.toIntOrNull()
            return Depth(level)
        }

    }

}