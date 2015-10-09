package at.bitfire.dav4android.property;

import android.util.Log;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

import at.bitfire.dav4android.Constants;
import at.bitfire.dav4android.Property;
import at.bitfire.dav4android.PropertyFactory;
import at.bitfire.dav4android.XmlUtils;
import lombok.ToString;

@ToString
public class CalendarColor implements Property {
    public static final Name NAME = new Name(XmlUtils.NS_APPLE_ICAL, "calendar-color");

    public String color;


    public static class Factory implements PropertyFactory {
        @Override
        public Name getName() {
            return NAME;
        }

        @Override
        public CalendarColor create(XmlPullParser parser) {
            CalendarColor calendarColor = new CalendarColor();

            try {
                final int depth = parser.getDepth();

                int eventType = parser.getEventType();
                while (eventType != XmlPullParser.END_DOCUMENT) {
                    if (eventType == XmlPullParser.TEXT && parser.getDepth() == depth) {
                        calendarColor.color = parser.getText();
                    } else if (eventType == XmlPullParser.END_TAG && parser.getDepth() == depth)
                        break;
                    eventType = parser.next();
                }
            } catch(XmlPullParserException |IOException e) {
                Log.e(Constants.LOG_TAG, "Couldn't parse <calendar-color>", e);
                return null;
            }

            return calendarColor;
        }
    }

}
