/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.dav4jvm

import nl.adaptivity.xmlutil.QName
import nl.adaptivity.xmlutil.XmlReader
import java.io.Serializable

/**
 * Represents an XML precondition/postcondition error. Every error has a name, which is the XML element
 * name. Subclassed errors may have more specific information available.
 *
 * At the moment, there is no logic for subclassing errors.
 */
class Error(
    val name: QName
) : Serializable {

    companion object {

        val NAME = QName(XmlUtils.NS_WEBDAV, "error")

        fun parseError(parser: XmlReader): List<Error> {
            val names = mutableSetOf<QName>()

            XmlUtils.processTag(parser) { names += parser.name }

            return names.map { Error(it) }
        }

        // some pre-defined errors

        val NEED_PRIVILEGES = Error(QName(XmlUtils.NS_WEBDAV, "need-privileges"))
        val VALID_SYNC_TOKEN = Error(QName(XmlUtils.NS_WEBDAV, "valid-sync-token"))
    }

    override fun equals(other: Any?) =
        (other is Error) && other.name == name

    override fun hashCode() = name.hashCode()
}
