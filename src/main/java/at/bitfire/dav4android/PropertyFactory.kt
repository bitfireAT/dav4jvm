package at.bitfire.dav4android;

import org.xmlpull.v1.XmlPullParser

interface PropertyFactory {

    /**
     * Name of the Property the factory creates,
     * e.g. Property.Name("DAV:", "displayname") if the factory creates DisplayName objects)
     */
    fun getName(): Property.Name

    fun create(parser: XmlPullParser): Property?

}
