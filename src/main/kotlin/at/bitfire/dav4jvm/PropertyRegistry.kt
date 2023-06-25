/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm

import at.bitfire.dav4jvm.property.AddMember
import at.bitfire.dav4jvm.property.AddressData
import at.bitfire.dav4jvm.property.AddressbookDescription
import at.bitfire.dav4jvm.property.AddressbookHomeSet
import at.bitfire.dav4jvm.property.CalendarColor
import at.bitfire.dav4jvm.property.CalendarData
import at.bitfire.dav4jvm.property.CalendarDescription
import at.bitfire.dav4jvm.property.CalendarHomeSet
import at.bitfire.dav4jvm.property.CalendarProxyReadFor
import at.bitfire.dav4jvm.property.CalendarProxyWriteFor
import at.bitfire.dav4jvm.property.CalendarTimezone
import at.bitfire.dav4jvm.property.CalendarUserAddressSet
import at.bitfire.dav4jvm.property.CreationDate
import at.bitfire.dav4jvm.property.CurrentUserPrincipal
import at.bitfire.dav4jvm.property.CurrentUserPrivilegeSet
import at.bitfire.dav4jvm.property.DisplayName
import at.bitfire.dav4jvm.property.GetCTag
import at.bitfire.dav4jvm.property.GetContentLength
import at.bitfire.dav4jvm.property.GetContentType
import at.bitfire.dav4jvm.property.GetETag
import at.bitfire.dav4jvm.property.GetLastModified
import at.bitfire.dav4jvm.property.GroupMembership
import at.bitfire.dav4jvm.property.MaxICalendarSize
import at.bitfire.dav4jvm.property.MaxVCardSize
import at.bitfire.dav4jvm.property.Owner
import at.bitfire.dav4jvm.property.QuotaAvailableBytes
import at.bitfire.dav4jvm.property.QuotaUsedBytes
import at.bitfire.dav4jvm.property.ResourceType
import at.bitfire.dav4jvm.property.ScheduleTag
import at.bitfire.dav4jvm.property.Source
import at.bitfire.dav4jvm.property.SupportedAddressData
import at.bitfire.dav4jvm.property.SupportedCalendarComponentSet
import at.bitfire.dav4jvm.property.SupportedCalendarData
import at.bitfire.dav4jvm.property.SupportedReportSet
import at.bitfire.dav4jvm.property.SyncToken
import nl.adaptivity.xmlutil.XmlException
import nl.adaptivity.xmlutil.XmlReader
import java.util.logging.Level
import javax.xml.namespace.QName

object PropertyRegistry {

    private val factories = mutableMapOf<QName, PropertyFactory>()

    init {
        Dav4jvm.log.info("Registering DAV property factories")
        registerDefaultFactories()
    }

    private fun registerDefaultFactories() {
        register(
            listOf(
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
            )
        )
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

    fun create(name: QName, parser: XmlReader) =
        try {
            factories[name]?.create(parser)
        } catch (e: XmlException) {
            Dav4jvm.log.log(Level.WARNING, "Couldn't parse $name", e)
            null
        }
}
