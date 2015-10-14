package at.bitfire.dav4android;

import org.xmlpull.v1.XmlPullParser;

import java.util.HashMap;
import java.util.Map;

import at.bitfire.dav4android.property.AddressData;
import at.bitfire.dav4android.property.AddressbookDescription;
import at.bitfire.dav4android.property.AddressbookHomeSet;
import at.bitfire.dav4android.property.CalendarColor;
import at.bitfire.dav4android.property.CalendarData;
import at.bitfire.dav4android.property.CalendarDescription;
import at.bitfire.dav4android.property.CalendarHomeSet;
import at.bitfire.dav4android.property.CalendarTimezone;
import at.bitfire.dav4android.property.CurrentUserPrincipal;
import at.bitfire.dav4android.property.CurrentUserPrivilegeSet;
import at.bitfire.dav4android.property.DisplayName;
import at.bitfire.dav4android.property.GetCTag;
import at.bitfire.dav4android.property.GetContentType;
import at.bitfire.dav4android.property.GetETag;
import at.bitfire.dav4android.property.ResourceType;
import at.bitfire.dav4android.property.SupportedAddressData;
import at.bitfire.dav4android.property.SupportedCalendarComponentSet;

public class PropertyRegistry {

    protected Map<String, Map<String, PropertyFactory>> factories = new HashMap<>();

    static final PropertyRegistry DEFAULT = new PropertyRegistry();
    static {
        DEFAULT.register(new ResourceType.Factory());
        DEFAULT.register(new DisplayName.Factory());
        DEFAULT.register(new GetCTag.Factory());
        DEFAULT.register(new GetETag.Factory());
        DEFAULT.register(new GetContentType.Factory());
        DEFAULT.register(new CurrentUserPrincipal.Factory());
        DEFAULT.register(new CurrentUserPrivilegeSet.Factory());

        // CardDAV
        DEFAULT.register(new AddressbookHomeSet.Factory());
        DEFAULT.register(new AddressbookDescription.Factory());
        DEFAULT.register(new SupportedAddressData.Factory());
        DEFAULT.register(new AddressData.Factory());

        // CalDAV
        DEFAULT.register(new CalendarHomeSet.Factory());
        DEFAULT.register(new CalendarColor.Factory());
        DEFAULT.register(new CalendarDescription.Factory());
        DEFAULT.register(new CalendarTimezone.Factory());
        DEFAULT.register(new SupportedCalendarComponentSet.Factory());
        DEFAULT.register(new CalendarData.Factory());
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
