package at.bitfire.dav4jvm.property

import at.bitfire.dav4jvm.Property
import io.kotest.core.spec.style.FunSpec
import nl.adaptivity.xmlutil.XmlStreaming

open class PropertyTest: FunSpec() {

    companion object {

        fun parseProperty(s: String): List<Property> {
            val parser = XmlStreaming.newReader("<test>$s</test>")
            parser.nextTag()    // move into <test>
            return Property.parse(parser)
        }

    }

}