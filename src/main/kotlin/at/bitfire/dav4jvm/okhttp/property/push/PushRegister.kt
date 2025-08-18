/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

package at.bitfire.dav4jvm.okhttp.property.push

import at.bitfire.dav4jvm.okhttp.HttpUtils
import at.bitfire.dav4jvm.okhttp.Property
import at.bitfire.dav4jvm.okhttp.PropertyFactory
import at.bitfire.dav4jvm.okhttp.XmlReader
import at.bitfire.dav4jvm.okhttp.XmlUtils.propertyName
import org.xmlpull.v1.XmlPullParser
import java.time.Instant

/**
 * Represents a [NS_WEBDAV_PUSH]`:push-register` property.
 *
 * Experimental! See https://github.com/bitfireAT/webdav-push/
 */
data class PushRegister(
    val expires: Instant? = null,
    val subscription: Subscription? = null,
    val trigger: Trigger? = null
): Property {

    companion object {

        @JvmField
        val NAME = Property.Name(NS_WEBDAV_PUSH, "push-register")

        val EXPIRES = Property.Name(NS_WEBDAV_PUSH, "expires")

    }


    object Factory: PropertyFactory {

        override fun getName() = NAME

        override fun create(parser: XmlPullParser): PushRegister {
            var register = PushRegister()

            val depth = parser.depth
            var eventType = parser.eventType
            while (!(eventType == XmlPullParser.END_TAG && parser.depth == depth)) {
                if (eventType == XmlPullParser.START_TAG && parser.depth == depth + 1)
                    when (parser.propertyName()) {
                        EXPIRES ->
                            register = register.copy(
                                expires = XmlReader(parser).readText()?.let {
                                    HttpUtils.parseDate(it)
                                }
                            )
                        Subscription.Companion.NAME ->
                            register = register.copy(
                                subscription = Subscription.Factory.create(parser)
                            )
                        Trigger.Companion.NAME ->
                            register = register.copy(
                                trigger = Trigger.Factory.create(parser)
                            )
                    }
                eventType = parser.next()
            }

            return register
        }

    }

}