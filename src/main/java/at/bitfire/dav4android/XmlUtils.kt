package at.bitfire.dav4android;

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory
import java.util.logging.Level

class XmlUtils {
    companion object {

        @JvmField val NS_WEBDAV = "DAV:"
        @JvmField val NS_CALDAV = "urn:ietf:params:xml:ns:caldav"
        @JvmField val NS_CARDDAV = "urn:ietf:params:xml:ns:carddav"
        @JvmField val NS_APPLE_ICAL = "http://apple.com/ns/ical/"
        @JvmField val NS_CALENDARSERVER = "http://calendarserver.org/ns/"

        private val factory: XmlPullParserFactory
        init {
            try {
                factory = XmlPullParserFactory.newInstance()
                factory.isNamespaceAware = true
            } catch (e: XmlPullParserException) {
                throw RuntimeException("Couldn't create XmlPullParserFactory", e)
            }
        }

        @JvmStatic
        fun newPullParser() = factory.newPullParser()!!

        @JvmStatic
        fun newSerializer() = factory.newSerializer()!!


        fun readText(parser: XmlPullParser): String? {
            var text: String? = null

            try {
                val depth = parser.depth

                var eventType = parser.eventType
                while (!(eventType == XmlPullParser.END_TAG && parser.depth == depth)) {
                    if (eventType == XmlPullParser.TEXT && parser.depth == depth)
                        text = parser.text
                    eventType = parser.next()
                }
            } catch(e: Exception) {
                Constants.log.log(Level.SEVERE, "Couldn't parse text property", e);
            }

            return text
        }

        fun readTextPropertyList(parser: XmlPullParser, name: Property.Name, list: MutableList<String>) {
            try {
                val depth = parser.depth

                var eventType = parser.eventType
                while (!(eventType == XmlPullParser.END_TAG && parser.depth == depth)) {
                    if (eventType == XmlPullParser.START_TAG && parser.depth == depth+1 &&
                            Property.Name(parser.namespace, parser.name) == name)
                        list.add(parser.nextText())
                    eventType = parser.next()
                }
            } catch(e: Exception) {
                Constants.log.log(Level.SEVERE, "Couldn't parse text property list", e)
            }
        }

    }
}
