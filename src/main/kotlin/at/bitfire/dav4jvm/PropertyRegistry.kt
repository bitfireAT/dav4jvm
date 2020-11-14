/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm

import at.bitfire.dav4jvm.property.*
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException
import java.util.logging.Level

object PropertyRegistry {

    private val factories = mutableMapOf<Property.Name, PropertyFactory>()

    init {
        Dav4jvm.log.info("Registering DAV property factories")
        registerDefaultFactories()
    }

    private fun registerDefaultFactories() {
        register(listOf(
            AddMember.Factory(),
            AddressbookDescription.Factory(),
            AddressbookHomeSet.Factory(),
            AddressData.Factory(),
            CalendarColor.Factory(),
            CalendarData.Factory(),
            CalendarDescription.Factory(),
            CalendarHomeSet.Factory(),
            CalendarProxyReadFor.Factory(),
            CalendarProxyWriteFor.Factory(),
            CalendarTimezone.Factory(),
            CalendarUserAddressSet.Factory(),
            CreationDate.Factory(),
            CurrentUserPrincipal.Factory(),
            CurrentUserPrivilegeSet.Factory(),
            DisplayName.Factory(),
            GetContentLength.Factory(),
            GetContentType.Factory(),
            GetCTag.Factory(),
            GetETag.Factory(),
            GetLastModified.Factory(),
            GroupMembership.Factory(),
            Owner.Factory(),
            QuotaAvailableBytes.Factory(),
            QuotaUsedBytes.Factory(),
            ResourceType.Factory(),
            ScheduleTag.Factory(),
            Source.Factory(),
            SupportedAddressData.Factory(),
            SupportedCalendarComponentSet.Factory(),
            SupportedReportSet.Factory(),
            SyncToken.Factory()
        ))
    }


    /**
     * Registers a property factory, so that objects for all WebDAV properties which are handled
     * by this factory can be created.
     *
     * @param factory property factory to be registered
     */
    fun register(factory: PropertyFactory) {
        Dav4jvm.log.fine("Registering ${factory::class.java.name} for ${factory.getName()}")
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
                Dav4jvm.log.log(Level.WARNING, "Couldn't parse $name", e)
                null
            }

}
