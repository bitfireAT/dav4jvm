/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.dav4android

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

interface PropertyFactory {

    /**
     * Name of the Property the factory creates,
     * e.g. Property.Name("DAV:", "displayname") if the factory creates DisplayName objects)
     */
    fun getName(): Property.Name

    /**
     * Parses XML of a property and returns its data class.
     * @throws XmlPullParserException in case of parsing errors
     */
    fun create(parser: XmlPullParser): Property?

}
