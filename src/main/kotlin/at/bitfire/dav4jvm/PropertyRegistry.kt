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
        register(ServiceLoader.load(PropertyFactory::class.java))
    }


    /**
     * Registers a property factory, so that objects for all WebDAV properties which are handled
     * by this factory can be created.
     *
     * @param factory property factory to be registered
     */
    fun register(factory: PropertyFactory) {
        Constants.log.fine("Registering ${factory::class.java.name} for ${factory.getName()}")
        factories[factory.getName()] = factory
    }

    /**
     * Registers some property factories, so that objects for all WebDAV properties which are handled
     * by these factories can be created.

     * @param factories property factories to be registered
     */
    fun register(factories: Iterable<PropertyFactory>) {
        factories.forEach {
            register(it)
        }
    }

    fun create(name: Property.Name, parser: XmlPullParser) =
            try {
                factories[name]?.create(parser)
            } catch (e: XmlPullParserException) {
                Constants.log.log(Level.WARNING, "Couldn't parse $name", e)
                null
            }

}
