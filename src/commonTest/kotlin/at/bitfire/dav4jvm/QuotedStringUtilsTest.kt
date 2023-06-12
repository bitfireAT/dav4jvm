/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package at.bitfire.dav4jvm

import io.kotest.core.spec.style.FunSpec
import kotlin.test.assertEquals

object QuotedStringUtilsTest : FunSpec({

    test("testAsQuotedString") {
        assertEquals("\"\"", QuotedStringUtils.asQuotedString(""))
        assertEquals("\"\\\"\"", QuotedStringUtils.asQuotedString("\""))
        assertEquals("\"\\\\\"", QuotedStringUtils.asQuotedString("\\"))
    }

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

})
