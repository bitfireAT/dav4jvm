package at.bitfire.dav4jvm.property

import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.XmlUtils
import org.kobjects.ktxml.mini.MiniXmlPullParser
import java.io.StringReader

open class PropertyTest {

    companion object {

        fun parseProperty(s: String): List<Property> {
            val parser = MiniXmlPullParser(StringReader("<test>$s</test>").toString().iterator())
            parser.nextTag()    // move into <test>
            return Property.parse(parser)
        }

    }

}