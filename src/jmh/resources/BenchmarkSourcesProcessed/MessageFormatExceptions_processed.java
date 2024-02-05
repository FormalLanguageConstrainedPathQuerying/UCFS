/*
 * Copyright (c) 2011, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * @test
 * @summary Validate some exceptions in MessageFormat
 * @bug 6481179 8039165
 * @run junit MessageFormatExceptions
 */

import java.text.MessageFormat;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class MessageFormatExceptions {

    @Test
    public void nullPatternTest() {
        assertThrows(NullPointerException.class, () -> new MessageFormat(null));
        assertThrows(NullPointerException.class, () -> new MessageFormat(null, Locale.US));
        assertThrows(NullPointerException.class,
                () -> MessageFormat.format(null, new Object[] { "val0", "val1" }));
    }

    @Test
    public void nullLocaleTest() {
        assertThrows(NullPointerException.class,
                () -> new MessageFormat("{0, date}", null));
        assertThrows(NullPointerException.class,
                () -> new MessageFormat("{0, number}", null));
        assertThrows(NullPointerException.class,
                () -> new MessageFormat("{0}", null).format(new Object[]{42}));
        MessageFormat msgFmt = new MessageFormat("{0}");
        msgFmt.setLocale(null);
        assertThrows(NullPointerException.class, () -> msgFmt.format(new Object[]{42}));
        assertDoesNotThrow(() ->
                new MessageFormat("{0}", null).format(new Object[]{"hello"}));

    }

    @Test
    public void formatMsgTest() {
        IllegalArgumentException iae = assertThrows(IllegalArgumentException.class,
                () -> MessageFormat.format("Testdata {1,invalid_format_type}", new Object[] { "val0", "val1" }));
        assertEquals("unknown format type: invalid_format_type", iae.getMessage());
    }
}
