package at.bitfire.dav4android;

import org.xmlpull.v1.XmlPullParser
import java.util.*

object PropertyRegistry {

    val factories = HashMap<String, HashMap<String, PropertyFactory>>()

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
            nsFactories = HashMap<String, PropertyFactory>()
            factories[name.namespace] = nsFactories
        }
        nsFactories[name.name] = factory;
    }

    fun create(name: Property.Name, parser: XmlPullParser): Property? {
        val map = factories[name.namespace]
        if (map != null) {
            val factory = map[name.name]
            if (factory != null)
                return factory.create(parser)
        }
        return null
    }

}
