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

import io.ktor.http.fromHttpToGmtDate
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Test class to reproduce Ktor behavior.
 */
class KtorTest {

    // String.fromHttpToGmtDate

    /* The following three tests reproduce https://youtrack.jetbrains.com/issue/KTOR-10200.
    As soon as they fail, the RFC 850 and asctime DateTimeFormatters can be removed from
    `at.bitfire.dav4jvm.HttpUtils.fallbackDateFormats` because Ktor then handles those formats. */

    @Test(expected = IllegalStateException::class)
    fun rfc850() {
        // date string copied from https://www.rfc-editor.org/info/rfc9110/#section-5.6.7
        assertEquals(784111777000L, "Sunday, 06-Nov-94 08:49:37 GMT".fromHttpToGmtDate().timestamp)
    }

    @Test(expected = IllegalStateException::class)
    fun asctime() {
        // date string copied from https://www.rfc-editor.org/info/rfc9110/#section-5.6.7
        assertEquals(784111777000L, "Sun Nov  6 08:49:37 1994".fromHttpToGmtDate().timestamp)
    }

    @Test(expected = IllegalStateException::class)
    fun asctimeTwoDigitDay() {
        assertEquals(1778799062000L, "Thu May 14 22:51:02 2026".fromHttpToGmtDate().timestamp)
    }

}
