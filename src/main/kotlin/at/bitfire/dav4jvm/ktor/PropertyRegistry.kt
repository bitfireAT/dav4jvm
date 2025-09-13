/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

package at.bitfire.dav4jvm.ktor

import at.bitfire.dav4jvm.ktor.property.caldav.CalendarColor
import at.bitfire.dav4jvm.ktor.property.caldav.CalendarData
import at.bitfire.dav4jvm.ktor.property.caldav.CalendarDescription
import at.bitfire.dav4jvm.ktor.property.caldav.CalendarHomeSet
import at.bitfire.dav4jvm.ktor.property.caldav.CalendarProxyReadFor
import at.bitfire.dav4jvm.ktor.property.caldav.CalendarProxyWriteFor
import at.bitfire.dav4jvm.ktor.property.caldav.CalendarTimezone
import at.bitfire.dav4jvm.ktor.property.caldav.CalendarTimezoneId
import at.bitfire.dav4jvm.ktor.property.caldav.CalendarUserAddressSet
import at.bitfire.dav4jvm.ktor.property.caldav.GetCTag
import at.bitfire.dav4jvm.ktor.property.caldav.MaxResourceSize
import at.bitfire.dav4jvm.ktor.property.caldav.ScheduleTag
import at.bitfire.dav4jvm.ktor.property.caldav.Source
import at.bitfire.dav4jvm.ktor.property.caldav.SupportedCalendarComponentSet
import at.bitfire.dav4jvm.ktor.property.caldav.SupportedCalendarData
import at.bitfire.dav4jvm.ktor.property.carddav.AddressData
import at.bitfire.dav4jvm.ktor.property.carddav.AddressbookDescription
import at.bitfire.dav4jvm.ktor.property.carddav.AddressbookHomeSet
import at.bitfire.dav4jvm.ktor.property.carddav.SupportedAddressData
import at.bitfire.dav4jvm.ktor.property.push.PushMessage
import at.bitfire.dav4jvm.ktor.property.push.PushRegister
import at.bitfire.dav4jvm.ktor.property.push.PushTransports
import at.bitfire.dav4jvm.ktor.property.push.Subscription
import at.bitfire.dav4jvm.ktor.property.push.Topic
import at.bitfire.dav4jvm.ktor.property.push.WebPushSubscription
import at.bitfire.dav4jvm.ktor.property.webdav.AddMember
import at.bitfire.dav4jvm.ktor.property.webdav.CreationDate
import at.bitfire.dav4jvm.ktor.property.webdav.CurrentUserPrincipal
import at.bitfire.dav4jvm.ktor.property.webdav.CurrentUserPrivilegeSet
import at.bitfire.dav4jvm.ktor.property.webdav.DisplayName
import at.bitfire.dav4jvm.ktor.property.webdav.GetContentLength
import at.bitfire.dav4jvm.ktor.property.webdav.GetContentType
import at.bitfire.dav4jvm.ktor.property.webdav.GetETag
import at.bitfire.dav4jvm.ktor.property.webdav.GetLastModified
import at.bitfire.dav4jvm.ktor.property.webdav.GroupMembership
import at.bitfire.dav4jvm.ktor.property.webdav.Owner
import at.bitfire.dav4jvm.ktor.property.webdav.QuotaAvailableBytes
import at.bitfire.dav4jvm.ktor.property.webdav.QuotaUsedBytes
import at.bitfire.dav4jvm.ktor.property.webdav.ResourceType
import at.bitfire.dav4jvm.ktor.property.webdav.SupportedReportSet
import at.bitfire.dav4jvm.ktor.property.webdav.SyncToken
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.util.logging.Level
import java.util.logging.Logger

object PropertyRegistry {

    private val factories = mutableMapOf<Property.Name, PropertyFactory>()
    private val logger
        get() = Logger.getLogger(javaClass.name)


    init {
        logger.info("Registering DAV property factories")
        registerDefaultFactories()
    }

    private fun registerDefaultFactories() {
        register(listOf(
            AddMember.Factory,
            AddressbookDescription.Factory,
            AddressbookHomeSet.Factory,
            AddressData.Factory,
            CalendarColor.Factory,
            CalendarData.Factory,
            CalendarDescription.Factory,
            CalendarHomeSet.Factory,
            CalendarProxyReadFor.Factory,
            CalendarProxyWriteFor.Factory,
            CalendarTimezone.Factory,
            CalendarTimezoneId.Factory,
            CalendarUserAddressSet.Factory,
            CreationDate.Factory,
            CurrentUserPrincipal.Factory,
            CurrentUserPrivilegeSet.Factory,
            DisplayName.Factory,
            GetContentLength.Factory,
            GetContentType.Factory,
            GetCTag.Factory,
            GetETag.Factory,
            GetLastModified.Factory,
            GroupMembership.Factory,
            MaxResourceSize.Factory,
            at.bitfire.dav4jvm.ktor.property.carddav.MaxResourceSize.Factory,
            Owner.Factory,
            PushMessage.Factory,
            PushRegister.Factory,
            PushTransports.Factory,
            QuotaAvailableBytes.Factory,
            QuotaUsedBytes.Factory,
            ResourceType.Factory,
            ScheduleTag.Factory,
            Source.Factory,
            Subscription.Factory,
            SupportedAddressData.Factory,
            SupportedCalendarComponentSet.Factory,
            SupportedCalendarData.Factory,
            SupportedReportSet.Factory,
            SyncToken.Factory,
            Topic.Factory,
            WebPushSubscription.Factory
        ))
    }


    /**
     * Registers a property factory, so that objects for all WebDAV properties which are handled
     * by this factory can be created.
     *
     * @param factory property factory to be registered
     */
    fun register(factory: PropertyFactory) {
        logger.fine("Registering ${factory::class.java.name} for ${factory.getName()}")
        factories[factory.getName()] = factory
    }

    /**
     * Registers some property factories, so that objects for all WebDAV properties which are handled
     * by these factories can be created.

     * @param factories property factories to be registered
     */
    fun register(factories: Iterable<PropertyFactory>) {
        factories.forEach {
            register(it)
        }
    }

    fun create(name: Property.Name, parser: XmlPullParser) =
        try {
            factories[name]?.create(parser)
        } catch (e: XmlPullParserException) {
            logger.log(Level.WARNING, "Couldn't parse $name", e)
            null
        }

}