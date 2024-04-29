/*
 * Copyright (c) 2015, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8139688
 * @key randomness
 * @library /test/lib
 * @build jdk.test.lib.RandomFactory
 * @build Tests
 * @build FdlibmTranslit
 * @build ExpTests
 * @run main ExpTests
 * @summary Tests specifically for StrictMath.exp
 */

import jdk.test.lib.RandomFactory;

/**
 * The role of this test is to verify that the FDLIBM exp algorithm is
 * being used by running golden file style tests on values that may
 * vary from one conforming exponential implementation to another.
 */

public class ExpTests {
    private ExpTests(){}

    public static void main(String [] argv) {
        int failures = 0;

        failures += testExp();
        failures += testAgainstTranslit();

        if (failures > 0) {
            System.err.println("Testing the exponential incurred "
                               + failures + " failures.");
            throw new RuntimeException();
        }
    }

    static final double EXP_OVERFLOW_THRESH  = Double.longBitsToDouble(0x4086_2E42_FEFA_39EFL);

    static final double EXP_UNDERFLOW_THRESH = Double.longBitsToDouble(0xc087_4910_D52D_3051L);

    static int testExp() {
        int failures = 0;

        double [][] testCases = {
            {Double.NaN,                      Double.NaN},
            {Double.MAX_VALUE,                Double.POSITIVE_INFINITY},
            {Double.POSITIVE_INFINITY,        Double.POSITIVE_INFINITY},
            {Double.NEGATIVE_INFINITY,        +0.0},
            {EXP_OVERFLOW_THRESH,                 0x1.ffff_ffff_fff2ap1023},
            {Math.nextUp(EXP_OVERFLOW_THRESH),    Double.POSITIVE_INFINITY},
            {Math.nextDown(EXP_UNDERFLOW_THRESH), +0.0},
            {EXP_UNDERFLOW_THRESH,                +Double.MIN_VALUE},

            {1.0,                                  Math.nextUp(Math.E)},
            {+0x1.2e8f20cf3cbe7p+8,                0x1.6a2a59cc78bf8p436},
            {-0x1.49f33ad2c1c58p+9,                0x1.f3ccc815431b6p-953},
            {+0x1.fce66609f7428p+5,                0x1.b59724cb0bc4cp91},
            {-0x1.49f33ad2c1c58p+9,                0x1.f3ccc815431b6p-953},
        };

        for(double[] testCase: testCases)
            failures+=testExpCase(testCase[0], testCase[1]);

        return failures;
    }

    static int testExpCase(double input, double expected) {
        int failures = 0;

        failures+=Tests.test("StrictMath.exp(double)", input,
                             StrictMath.exp(input), expected);
        return failures;
    }

    private static java.util.Random random = RandomFactory.getRandom();

    /**
     * Test StrictMath.exp against transliteration port of exp.
     */
    private static int testAgainstTranslit() {
        int failures = 0;

        double[] decisionPoints = {
            EXP_OVERFLOW_THRESH - 512*Math.ulp(EXP_OVERFLOW_THRESH),

            EXP_UNDERFLOW_THRESH - 512*Math.ulp(EXP_UNDERFLOW_THRESH),

            Double.longBitsToDouble(0x4086_2E42_0000_0000L - 512L),
            Double.longBitsToDouble(0x3fd6_2e42_0000_0000L - 512L),
            Double.longBitsToDouble(0x3FF0_A2B2_0000_0000L - 512L),
            Double.longBitsToDouble(0x3e30_0000_0000_0000L - 512L),

            Double.MIN_NORMAL - Math.ulp(Double.MIN_NORMAL)*512,
            -Double.MIN_VALUE*512,
        };

        for (double decisionPoint : decisionPoints) {
            double ulp = Math.ulp(decisionPoint);
            failures += testRange(decisionPoint - 1024*ulp, ulp, 1_024);
        }

        for (int i = 0; i < 100; i++) {
            double x = Tests.createRandomDouble(random);
            failures += testRange(x, Math.ulp(x), 100);
        }

        return failures;
    }

    private static int testRange(double start, double increment, int count) {
        int failures = 0;
        double x = start;
        for (int i = 0; i < count; i++, x += increment) {
            failures += testExpCase(x, FdlibmTranslit.exp(x));
        }
        return failures;
    }
}
