/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm

import nl.adaptivity.xmlutil.QName
import nl.adaptivity.xmlutil.XmlReader

interface PropertyFactory {

    /**
     * Name of the Property the factory creates,
     * e.g. QName("DAV:", "displayname") if the factory creates DisplayName objects)
     */
    fun getName(): QName

    /**
     * Parses XML of a property and returns its data class.
     * @throws XmlPullParserException in case of parsing errors
     */
    fun create(parser: XmlReader): Property?

}
