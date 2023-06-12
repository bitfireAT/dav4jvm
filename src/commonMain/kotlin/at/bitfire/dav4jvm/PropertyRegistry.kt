/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm

import at.bitfire.dav4jvm.property.*
import nl.adaptivity.xmlutil.QName
import nl.adaptivity.xmlutil.XmlException
import nl.adaptivity.xmlutil.XmlReader

object PropertyRegistry {

    private val factories = mutableMapOf<QName, PropertyFactory>()

    init {
        Dav4jvm.log.info("Registering DAV property factories")
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
            MaxICalendarSize.Factory,
            MaxVCardSize.Factory,
            Owner.Factory,
            QuotaAvailableBytes.Factory,
            QuotaUsedBytes.Factory,
            ResourceType.Factory,
            ScheduleTag.Factory,
            Source.Factory,
            SupportedAddressData.Factory,
            SupportedCalendarComponentSet.Factory,
            SupportedCalendarData.Factory,
            SupportedReportSet.Factory,
            SyncToken.Factory
        ))
    }


    /**
     * Registers a property factory, so that objects for all WebDAV properties which are handled
     * by this factory can be created.
     *
     * @param factory property factory to be registered
     */
    fun register(factory: PropertyFactory) {
        Dav4jvm.log.trace("Registering ${factory::class.simpleName} for ${factory.getName()}")
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

    fun create(name: QName, parser: XmlReader) =
            try {
                factories[name]?.create(parser)
            } catch (e: XmlException) {
                Dav4jvm.log.warn("Couldn't parse $name", e)
                null
            }

}
