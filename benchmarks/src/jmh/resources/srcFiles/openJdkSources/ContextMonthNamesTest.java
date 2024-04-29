/*
 * Copyright (c) 2012, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 7079560 8008577 8174269
 * @summary Unit test for context-sensitive month names
 * @modules jdk.localedata
 * @run main ContextMonthNamesTest
 */

import java.text.*;
import java.util.*;

public class ContextMonthNamesTest {
    static Locale CZECH = Locale.of("cs");
    static Date JAN30 = new GregorianCalendar(2012, Calendar.JANUARY, 30).getTime();

    static String[] PATTERNS = {
        "d. MMMM yyyy", 
        "d. MMM yyyy",  
        "MMMM",         
        "MMM",          
        "d. LLLL yyyy", 
        "d. LLL yyyy",  
    };
    static String[] EXPECTED = {
        "30. ledna 2012",
        "30. led 2012",
        "leden",
        "led",
        "30. leden 2012",
        "30. led 2012",
    };

    public static void main(String[] args) {
        SimpleDateFormat fmt = new SimpleDateFormat("", CZECH);
        for (int i = 0; i < PATTERNS.length; i++) {
            fmt.applyPattern(PATTERNS[i]);
            String str = fmt.format(JAN30);
            if (!EXPECTED[i].equals(str)) {
                throw new RuntimeException("bad result: got '" + str
                                           + "', expected '" + EXPECTED[i] + "'");
            }
        }
    }
}
