package at.bitfire.dav4android.property;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;

import at.bitfire.dav4android.Constants;
import at.bitfire.dav4android.Property;
import at.bitfire.dav4android.PropertyFactory;
import at.bitfire.dav4android.XmlUtils;
import okhttp3.MediaType;

public class SupportedAddressData implements Property {
    public static final Property.Name NAME = new Property.Name(XmlUtils.NS_CARDDAV, "supported-address-data");

    public final Set<MediaType> types = new HashSet<>();

    public boolean hasVCard4() {
        for (MediaType type : types)
            if ("text/vcard; version=4.0".equalsIgnoreCase(type.toString()))    // the literal string has been constructed exactly this way below
                return true;
        return false;
    }


    public static class Factory implements PropertyFactory {
        @Override
        public Property.Name getName() {
            return NAME;
        }

        @Override
        public SupportedAddressData create(XmlPullParser parser) {
            SupportedAddressData supported = new SupportedAddressData();

            try {
                final int depth = parser.getDepth();

                int eventType = parser.getEventType();
                while (!(eventType == XmlPullParser.END_TAG && parser.getDepth() == depth)) {
                    if (eventType == XmlPullParser.START_TAG && parser.getDepth() == depth+1 &&
                            XmlUtils.NS_CARDDAV.equals(parser.getNamespace()) && "address-data-type".equals(parser.getName())) {
                        String  contentType = parser.getAttributeValue(null, "content-type"),
                                version = parser.getAttributeValue(null, "version");
                        if (contentType != null) {
                            if (version != null)
                                contentType += "; version=" + version;
                            supported.types.add(MediaType.parse(contentType));
                        }
                    }
                    eventType = parser.next();
                }
            } catch(XmlPullParserException|IOException e) {
                Constants.log.log(Level.SEVERE, "Couldn't parse <resourcetype>", e);
                return null;
            }

            return supported;
        }
    }
}
