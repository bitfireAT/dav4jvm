/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm

import at.bitfire.dav4jvm.property.caldav.*
import at.bitfire.dav4jvm.property.carddav.AddressData
import at.bitfire.dav4jvm.property.carddav.AddressbookDescription
import at.bitfire.dav4jvm.property.carddav.AddressbookHomeSet
import at.bitfire.dav4jvm.property.carddav.SupportedAddressData
import at.bitfire.dav4jvm.property.push.*
import at.bitfire.dav4jvm.property.webdav.*
import java.util.logging.Level
import java.util.logging.Logger
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

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
            at.bitfire.dav4jvm.property.caldav.MaxResourceSize.Factory,
            at.bitfire.dav4jvm.property.carddav.MaxResourceSize.Factory,
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