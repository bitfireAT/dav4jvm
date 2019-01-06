/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.util.*
import java.util.logging.Level

object PropertyRegistry {

    private val factories = mutableMapOf<Property.Name, PropertyFactory>()

    init {
        Constants.log.info("Registering DAV property factories")
        for (factory in ServiceLoader.load(PropertyFactory::class.java)) {
            Constants.log.fine("Registering ${factory::class.java.name} for ${factory.getName()}")
            register(factory)
        }
    }


    private fun register(factory: PropertyFactory) {
        factories[factory.getName()] = factory
    }

    fun create(name: Property.Name, parser: XmlPullParser) =
            try {
                factories[name]?.create(parser)
            } catch (e: XmlPullParserException) {
                Constants.log.log(Level.WARNING, "Couldn't parse $name", e)
                null
            }

}
