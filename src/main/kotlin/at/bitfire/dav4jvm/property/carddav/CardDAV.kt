/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

package at.bitfire.dav4jvm.property.carddav

import at.bitfire.dav4jvm.Property

object CardDAV {

    // CardDAV (RFC 6352)

    const val NS_CARDDAV = "urn:ietf:params:xml:ns:carddav"

    val Addressbook = Property.Name(NS_CARDDAV, "addressbook") // CardDAV
    val AddressData = Property.Name(NS_CARDDAV, "address-data")
    val AddressDataType = Property.Name(NS_CARDDAV, "address-data-type")
    val AddressbookDescription = Property.Name(NS_CARDDAV, "addressbook-description")
    val AddressbookHomeSet = Property.Name(NS_CARDDAV, "addressbook-home-set")
    val AddressbookMultiget = Property.Name(NS_CARDDAV, "addressbook-multiget")
    val AddressbookQuery = Property.Name(NS_CARDDAV, "addressbook-query")
    val Filter = Property.Name(NS_CARDDAV, "filter")
    val MaxResourceSize = Property.Name(NS_CARDDAV, "max-resource-size")
    val SupportedAddressData = Property.Name(NS_CARDDAV, "supported-address-data")

}