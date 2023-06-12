/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm

import io.kotest.core.spec.style.FunSpec
import nl.adaptivity.xmlutil.QName
import kotlin.test.assertTrue

object ErrorTest : FunSpec({

    test("testEquals") {
        val errors = listOf(Error(QName("DAV:", "valid-sync-token")))
        assertTrue(errors.contains(Error.VALID_SYNC_TOKEN))
    }

})