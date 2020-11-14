package at.bitfire.dav4jvm.property

import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.XmlUtils
import java.io.StringReader

open class PropertyTest {

    companion object {

        fun parseProperty(s: String): List<Property> {
            val parser = XmlUtils.newPullParser()
            parser.setInput(StringReader("<test>$s</test>"))
            parser.nextTag()    // move into <test>
            return Property.parse(parser)
        }

    }

}