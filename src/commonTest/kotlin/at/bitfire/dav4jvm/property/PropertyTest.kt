package at.bitfire.dav4jvm.property

import at.bitfire.dav4jvm.Property
import at.bitfire.dav4jvm.XmlUtils
import io.kotest.core.spec.style.FunSpec

open class PropertyTest : FunSpec() {

    companion object {

        fun parseProperty(s: String): List<Property> {
            val parser = XmlUtils.createReader("<test>$s</test>")
            parser.nextTag()    // move into <test>
            return Property.parse(parser)
        }

    }

}