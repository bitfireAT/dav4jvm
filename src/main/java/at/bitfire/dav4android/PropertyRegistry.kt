/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.dav4android;

import org.xmlpull.v1.XmlPullParser
import java.util.*

object PropertyRegistry {

    val factories = mutableMapOf<String /*namespace*/, MutableMap<String /*name*/, PropertyFactory>>()

    init {
        Constants.log.info("Registering DAV property factories");
        for (factory in ServiceLoader.load(PropertyFactory::class.java)) {
            Constants.log.fine("Registering " + factory::class.java.name + " for " + factory.getName())
            register(factory)
        }
    }


    fun register(factory: PropertyFactory) {
        val name = factory.getName()
        var nsFactories = factories[name.namespace]
        if (nsFactories == null) {
            nsFactories = mutableMapOf<String, PropertyFactory>()
            factories[name.namespace] = nsFactories
        }
        nsFactories[name.name] = factory
    }

    fun create(name: Property.Name, parser: XmlPullParser): Property? {
        factories[name.namespace]?.let { nsFactories ->
            nsFactories[name.name]?.let { factory -> return factory.create(parser) }
        }
        return null
    }

}
