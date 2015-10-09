package at.bitfire.dav4android;

import org.xmlpull.v1.XmlPullParser;

public interface PropertyFactory {

    /**
     * @return name of the Property the factory creates,
     * e.g. Property.Name("DAV:", "displayname") if the factory creates DisplayName objects)
     */
    Property.Name getName();

    Property create(XmlPullParser parser);

}
