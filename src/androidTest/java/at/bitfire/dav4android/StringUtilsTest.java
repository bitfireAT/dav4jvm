/*
 * Copyright © 2013 – 2015 Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.dav4android;

import junit.framework.TestCase;

public class StringUtilsTest extends TestCase {

    public void testAsQuotedString() {
        assertEquals(null, StringUtils.asQuotedString(null));
        assertEquals("\"\"", StringUtils.asQuotedString(""));
        assertEquals("\"\\\"\"", StringUtils.asQuotedString("\""));
        assertEquals("\"\\\\\"", StringUtils.asQuotedString("\\"));
    }

    public void testDecodeQuotedString() {
        assertEquals(null, StringUtils.decodeQuotedString(null));
        assertEquals("\"", StringUtils.decodeQuotedString("\""));
        assertEquals("\\", StringUtils.decodeQuotedString("\"\\\""));
        assertEquals("\"test", StringUtils.decodeQuotedString("\"test"));
        assertEquals("test", StringUtils.decodeQuotedString("test"));
        assertEquals("", StringUtils.decodeQuotedString("\"\""));
        assertEquals("test", StringUtils.decodeQuotedString("\"test\""));
        assertEquals("test\\", StringUtils.decodeQuotedString("\"test\\\""));
        assertEquals("test", StringUtils.decodeQuotedString("\"t\\e\\st\""));
        assertEquals("12\"34", StringUtils.decodeQuotedString("\"12\\\"34\""));
        assertEquals("1234\"", StringUtils.decodeQuotedString("\"1234\\\"\""));
    }

}
