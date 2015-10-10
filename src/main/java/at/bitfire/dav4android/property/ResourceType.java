package at.bitfire.dav4android.property;

import android.util.Log;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import at.bitfire.dav4android.Constants;
import at.bitfire.dav4android.Property;
import at.bitfire.dav4android.PropertyFactory;
import at.bitfire.dav4android.XmlUtils;
import lombok.ToString;

@ToString
public class ResourceType implements Property {
    public static final Name NAME = new Name(XmlUtils.NS_WEBDAV, "resourcetype");

    public static final Name
            COLLECTION = new Name(XmlUtils.NS_WEBDAV, "collection"),
            ADDRESSBOOK = new Name(XmlUtils.NS_CARDDAV, "addressbook"),
            CALENDAR = new Name(XmlUtils.NS_CALDAV, "calendar");

    public final Set<Property.Name> types = new HashSet<>();


    public static class Factory implements PropertyFactory {
        @Override
        public Name getName() {
            return NAME;
        }

        @Override
        public ResourceType create(XmlPullParser parser) {
            ResourceType type = new ResourceType();

            try {
                final int depth = parser.getDepth();

                int eventType = parser.getEventType();
                while (!(eventType == XmlPullParser.END_TAG && parser.getDepth() == depth)) {
                    if (eventType == XmlPullParser.START_TAG && parser.getDepth() == depth + 1) {
                        String namespace = parser.getNamespace(), name = parser.getName();

                        // use static objects to allow types.contains()
                        Name typeName = new Name(parser.getNamespace(), parser.getName());
                        if (COLLECTION.equals(typeName))
                            typeName = COLLECTION;
                        else if (ADDRESSBOOK.equals(typeName))
                            typeName = ADDRESSBOOK;
                        else if (CALENDAR.equals(typeName))
                            typeName = CALENDAR;

                        type.types.add(typeName);
                    }
                    eventType = parser.next();
                }
            } catch(XmlPullParserException|IOException e) {
                Log.e(Constants.LOG_TAG, "Couldn't parse <resourcetype>", e);
                return null;
            }

            return type;
        }
    }
}
