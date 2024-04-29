/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package compiler.c2.irTests;

import jdk.test.lib.Asserts;
import compiler.lib.ir_framework.*;

/*
 * @test
 * @bug 8267265
 * @summary Test that Ideal transformations of DivINode* are being performed as expected.
 * @library /test/lib /
 * @run driver compiler.c2.irTests.DivINodeIdealizationTests
 */
public class DivINodeIdealizationTests {
    public static void main(String[] args) {
        TestFramework.run();
    }

    @Run(test = {"constant", "identity", "identityAgain", "identityThird",
                 "retainDenominator", "divByNegOne", "divByPow2And",
                 "divByPow2And1",  "divByPow2", "divByNegPow2",
                 "magicDiv"})
    public void runMethod() {
        int a = RunInfo.getRandom().nextInt();
            a = (a == 0) ? 1 : a;
        int b = RunInfo.getRandom().nextInt();
            b = (b == 0) ? 1 : b;

        int min = Integer.MIN_VALUE;
        int max = Integer.MAX_VALUE;

        assertResult(0, 0, true);
        assertResult(a, b, false);
        assertResult(min, min, false);
        assertResult(max, max, false);
    }

    @DontCompile
    public void assertResult(int a, int b, boolean shouldThrow) {
        try {
            Asserts.assertEQ(a / a, constant(a));
            Asserts.assertFalse(shouldThrow, "Expected an exception to be thrown.");
        }
        catch (ArithmeticException e) {
            Asserts.assertTrue(shouldThrow, "Did not expected an exception to be thrown.");
        }

        try {
            Asserts.assertEQ(a / (b / b), identityThird(a, b));
            Asserts.assertFalse(shouldThrow, "Expected an exception to be thrown.");
        }
        catch (ArithmeticException e) {
            Asserts.assertTrue(shouldThrow, "Did not expected an exception to be thrown.");
        }

        try {
            Asserts.assertEQ((a * b) / b, retainDenominator(a, b));
            Asserts.assertFalse(shouldThrow, "Expected an exception to be thrown.");
        }
        catch (ArithmeticException e) {
            Asserts.assertTrue(shouldThrow, "Did not expected an exception to be thrown.");
        }

        Asserts.assertEQ(a / 1        , identity(a));
        Asserts.assertEQ(a / (13 / 13), identityAgain(a));
        Asserts.assertEQ(a / -1       , divByNegOne(a));
        Asserts.assertEQ((a & -4) / 2 , divByPow2And(a));
        Asserts.assertEQ((a & -2) / 2 , divByPow2And1(a));
        Asserts.assertEQ(a / 8        , divByPow2(a));
        Asserts.assertEQ(a / -8       , divByNegPow2(a));
        Asserts.assertEQ(a / 13       , magicDiv(a));
    }

    @Test
    @IR(failOn = {IRNode.DIV})
    @IR(counts = {IRNode.DIV_BY_ZERO_TRAP, "1"})
    public int constant(int x) {
        return x / x;
    }

    @Test
    @IR(failOn = {IRNode.DIV})
    public int identity(int x) {
        return x / 1;
    }

    @Test
    @IR(failOn = {IRNode.DIV})
    public int identityAgain(int x) {
        return x / (13 / 13);
    }

    @Test
    @IR(failOn = {IRNode.DIV})
    @IR(counts = {IRNode.DIV_BY_ZERO_TRAP, "1"})
    public int identityThird(int x, int y) {
        return x / (y / y);
    }

    @Test
    @IR(counts = {IRNode.MUL, "1",
                  IRNode.DIV, "1",
                  IRNode.DIV_BY_ZERO_TRAP, "1"
                 })
    public int retainDenominator(int x, int y) {
        return (x * y) / y;
    }

    @Test
    @IR(failOn = {IRNode.DIV})
    @IR(counts = {IRNode.SUB_I, "1"})
    public int divByNegOne(int x) {
        return x / -1;
    }

    @Test
    @IR(failOn = {IRNode.DIV})
    @IR(counts = {IRNode.AND, "1",
                  IRNode.RSHIFT, "1",
                 })
    public int divByPow2And(int x) {
        return (x & -4) / 2;
    }

    @Test
    @IR(failOn = {IRNode.DIV, IRNode.AND})
    @IR(counts = {IRNode.RSHIFT, "1"})
    public int divByPow2And1(int x) {
        return (x & -2) / 2;
    }

    @Test
    @IR(failOn = {IRNode.DIV})
    @IR(counts = {IRNode.URSHIFT, "1",
                  IRNode.RSHIFT, "2",
                  IRNode.ADD_I, "1",
                 })
    public int divByPow2(int x) {
        return x / 8;
    }

    @Test
    @IR(failOn = {IRNode.DIV})
    @IR(counts = {IRNode.URSHIFT, "1",
                  IRNode.RSHIFT, "2",
                  IRNode.ADD_I, "1",
                  IRNode.SUB_I, "1",
                 })
    public int divByNegPow2(int x) {
        return x / -8;
    }

    @Test
    @IR(failOn = {IRNode.DIV})
    @IR(counts = {IRNode.SUB, "1",
                  IRNode.MUL, "1",
                  IRNode.CONV_I2L, "1",
                  IRNode.CONV_L2I, "1",
                 })
    public int magicDiv(int x) {
        return x / 13;
    }
}
