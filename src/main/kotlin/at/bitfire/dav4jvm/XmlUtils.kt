/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm

import at.bitfire.dav4jvm.exception.DavException
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import org.xmlpull.v1.XmlPullParserFactory
import org.xmlpull.v1.XmlSerializer

object XmlUtils {

    /**
     * Requests the parser to be as lenient as possible when parsing invalid XML.
     *
     * See [https://www.xmlpull.org/](xmlpull.org) and specific implementations, for instance
     * [Android XML](https://developer.android.com/reference/android/util/Xml#FEATURE_RELAXED)
     */
    private const val FEATURE_RELAXED = "http://xmlpull.org/v1/doc/features.html#relaxed"

    /** [XmlPullParserFactory] that is namespace-aware and does relaxed parsing */
    private val relaxedFactory =
        XmlPullParserFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature(FEATURE_RELAXED, true)
        }

    /** [XmlPullParserFactory] that is namespace-aware */
    private val standardFactory: XmlPullParserFactory =
        XmlPullParserFactory.newInstance().apply {
            isNamespaceAware = true
        }

    /**
     * Creates a new [XmlPullParser].
     *
     * First tries to create a namespace-aware parser that supports [FEATURE_RELAXED]. If that
     * fails, it falls back to a namespace-aware parser without relaxed parsing.
     */
    fun newPullParser(): XmlPullParser =
        try {
            relaxedFactory.newPullParser()
        } catch (_: XmlPullParserException) {
            // FEATURE_RELAXED may not be supported, try without it
            null
        }
        ?: standardFactory.newPullParser()
        ?: throw DavException("Couldn't create XML parser")

    fun newSerializer(): XmlSerializer = standardFactory.newSerializer()
        ?: throw DavException("Couldn't create XML serializer")


    fun XmlSerializer.insertTag(name: Property.Name, contentGenerator: XmlSerializer.() -> Unit = {}) {
        startTag(name.namespace, name.name)
        contentGenerator(this)
        endTag(name.namespace, name.name)
    }

    fun XmlPullParser.propertyName(): Property.Name {
        val propNs = namespace
        val propName = name
        if (propNs == null || propName == null)
            throw IllegalStateException("Current event must be START_TAG or END_TAG")
        return Property.Name(propNs, propName)
    }

}
