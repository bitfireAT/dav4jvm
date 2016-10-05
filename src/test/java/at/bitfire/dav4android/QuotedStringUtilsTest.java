/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.dav4android;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class QuotedStringUtilsTest {

    @Test
    public void testAsQuotedString() {
        assertEquals(null, QuotedStringUtils.asQuotedString(null));
        assertEquals("\"\"", QuotedStringUtils.asQuotedString(""));
        assertEquals("\"\\\"\"", QuotedStringUtils.asQuotedString("\""));
        assertEquals("\"\\\\\"", QuotedStringUtils.asQuotedString("\\"));
    }

    public void testDecodeQuotedString() {
        assertEquals(null, QuotedStringUtils.decodeQuotedString(null));
        assertEquals("\"", QuotedStringUtils.decodeQuotedString("\""));
        assertEquals("\\", QuotedStringUtils.decodeQuotedString("\"\\\""));
        assertEquals("\"test", QuotedStringUtils.decodeQuotedString("\"test"));
        assertEquals("test", QuotedStringUtils.decodeQuotedString("test"));
        assertEquals("", QuotedStringUtils.decodeQuotedString("\"\""));
        assertEquals("test", QuotedStringUtils.decodeQuotedString("\"test\""));
        assertEquals("test\\", QuotedStringUtils.decodeQuotedString("\"test\\\""));
        assertEquals("test", QuotedStringUtils.decodeQuotedString("\"t\\e\\st\""));
        assertEquals("12\"34", QuotedStringUtils.decodeQuotedString("\"12\\\"34\""));
        assertEquals("1234\"", QuotedStringUtils.decodeQuotedString("\"1234\\\"\""));
    }

}
