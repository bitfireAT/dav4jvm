package at.bitfire.dav4android;

import org.xmlpull.v1.XmlPullParser;

import java.util.HashMap;
import java.util.Map;

import at.bitfire.dav4android.property.CalendarColor;
import at.bitfire.dav4android.property.DisplayName;

public class PropertyRegistry {

    protected Map<String, Map<String, PropertyFactory>> factories = new HashMap<>();

    static final PropertyRegistry DEFAULT = new PropertyRegistry();
    static {
        DEFAULT.register(new DisplayName.Factory());
        DEFAULT.register(new CalendarColor.Factory());
    }


    public void register(PropertyFactory factory) {
        Property.Name name = factory.getName();
        Map<String, PropertyFactory> nsFactories = factories.get(name.namespace);
        if (nsFactories == null)
            factories.put(name.namespace, nsFactories = new HashMap<>());
        nsFactories.put(name.name, factory);
    }

    public Property create(Property.Name name, XmlPullParser parser) {
        Map<String, PropertyFactory> map = factories.get(name.namespace);
        if (map != null) {
            PropertyFactory factory = map.get(name.name);
            if (factory != null)
                return factory.create(parser);
        }
        return null;
    }

}
