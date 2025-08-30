/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

package at.bitfire.dav4jvm.okhttp

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

interface PropertyFactory {

    /**
     * Name of the Property the factory creates,
     * e.g. `Property.Name("DAV:", "displayname")` if the factory creates
     * [at.bitfire.dav4jvm.property.webdav.DisplayName] objects)
     */
    fun getName(): Property.Name

    /**
     * Parses XML of a property and returns its data class.
     *
     * Implementations shouldn't make assumptions on which sub-properties are available
     * or not and in doubt return an empty [Property].
     *
     * @throws XmlPullParserException in case of parsing errors
     */
    fun create(parser: XmlPullParser): Property

}
