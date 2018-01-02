/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.dav4android

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
