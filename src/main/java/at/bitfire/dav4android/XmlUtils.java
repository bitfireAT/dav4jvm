package at.bitfire.dav4android;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmlpull.v1.XmlSerializer;

import lombok.SneakyThrows;

public class XmlUtils {

    public static final String
            NS_WEBDAV = "DAV:",
            NS_CALDAV = "urn:ietf:params:xml:ns:caldav",
            NS_CARDDAV = "urn:ietf:params:xml:ns:carddav",
            NS_APPLE_ICAL = "http://apple.com/ns/ical/",
            NS_CALENDARSERVER = "http://calendarserver.org/ns/";

    private static final XmlPullParserFactory factory;
    static {
        try {
            factory = XmlPullParserFactory.newInstance();
            factory.setNamespaceAware(true);
        } catch (XmlPullParserException e) {
            throw new RuntimeException("Couldn't create XmlPullParserFactory", e);
        }
    }

    @SneakyThrows(XmlPullParserException.class)
    public static XmlPullParser newPullParser() {
        return factory.newPullParser();
    }

    @SneakyThrows(XmlPullParserException.class)
    public static XmlSerializer newSerializer() {
        return factory.newSerializer();
    }

}
