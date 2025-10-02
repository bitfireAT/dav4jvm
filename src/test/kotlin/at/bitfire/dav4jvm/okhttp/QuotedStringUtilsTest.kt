/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * SPDX-License-Identifier: MPL-2.0
 */

package at.bitfire.dav4jvm.okhttp

import at.bitfire.dav4jvm.QuotedStringUtils
import org.junit.Assert.assertEquals
import org.junit.Test

class QuotedStringUtilsTest {

    @Test
    fun testAsQuotedString() {
        assertEquals("\"\"", QuotedStringUtils.asQuotedString(""))
        assertEquals("\"\\\"\"", QuotedStringUtils.asQuotedString("\""))
        assertEquals("\"\\\\\"", QuotedStringUtils.asQuotedString("\\"))
    }

    @Test
    fun testDecodeQuotedString() {
        assertEquals("\"", QuotedStringUtils.decodeQuotedString("\""))
        assertEquals("\\", QuotedStringUtils.decodeQuotedString("\"\\\""))
        assertEquals("\"test", QuotedStringUtils.decodeQuotedString("\"test"))
        assertEquals("test", QuotedStringUtils.decodeQuotedString("test"))
        assertEquals("", QuotedStringUtils.decodeQuotedString("\"\""))
        assertEquals("test", QuotedStringUtils.decodeQuotedString("\"test\""))
        assertEquals("test\\", QuotedStringUtils.decodeQuotedString("\"test\\\""))
        assertEquals("test", QuotedStringUtils.decodeQuotedString("\"t\\e\\st\""))
        assertEquals("12\"34", QuotedStringUtils.decodeQuotedString("\"12\\\"34\""))
        assertEquals("1234\"", QuotedStringUtils.decodeQuotedString("\"1234\\\"\""))
    }

}
