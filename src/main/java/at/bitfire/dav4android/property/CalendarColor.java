package at.bitfire.dav4android.property;

import android.util.Log;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import at.bitfire.dav4android.Constants;
import at.bitfire.dav4android.Property;
import at.bitfire.dav4android.PropertyFactory;
import at.bitfire.dav4android.XmlUtils;
import lombok.ToString;

@ToString
public class CalendarColor implements Property {
    public static final Name NAME = new Name(XmlUtils.NS_APPLE_ICAL, "calendar-color");

    public Integer color;


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
                while (!(eventType == XmlPullParser.END_TAG && parser.getDepth() == depth)) {
                    if (eventType == XmlPullParser.TEXT && parser.getDepth() == depth)
                        calendarColor.color = parseARGBColor(parser.getText());
                    eventType = parser.next();
                }
            } catch(XmlPullParserException|IOException e) {
                Log.e(Constants.LOG_TAG, "Couldn't parse <calendar-color>", e);
                return null;
            }

            return calendarColor;
        }

        public static Integer parseARGBColor(String davColor) {
            Integer color = null;
            if (davColor != null) {
                Pattern p = Pattern.compile("#?(\\p{XDigit}{6})(\\p{XDigit}{2})?");
                Matcher m = p.matcher(davColor);
                if (m.find()) {
                    int color_rgb = Integer.parseInt(m.group(1), 16);
                    int color_alpha = m.group(2) != null ? (Integer.parseInt(m.group(2), 16) & 0xFF) : 0xFF;
                    color = (color_alpha << 24) | color_rgb;
                } else
                    Constants.log.warn("Couldn't parse color " + davColor + ", ignoring");
            }
            return color;
        }

    }
}
