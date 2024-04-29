/*
 * Copyright (c) 1997, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @summary test for Character Iterator
 * @run junit CharacterIteratorTest
 */

/*
 * This file is available under and governed by the GNU General Public
 * License version 2 only, as published by the Free Software Foundation.
 * However, the following notice accompanied the original version of this
 * file and, per its terms, should not be removed:
 *
 * (C) Copyright Taligent, Inc. 1996, 1997 - All Rights Reserved
 * (C) Copyright IBM Corp. 1996 - 1998 - All Rights Reserved
 *
 * Portions copyright (c) 2007 Sun Microsystems, Inc.
 * All Rights Reserved.
 *
 * The original version of this source code and documentation
 * is copyrighted and owned by Taligent, Inc., a wholly-owned
 * subsidiary of IBM. These materials are provided under terms
 * of a License Agreement between Taligent and Sun. This technology
 * is protected by multiple US and International patents.
 *
 * This notice and attribution to Taligent may not be removed.
 * Taligent is a registered trademark of Taligent, Inc.
 *
 * Permission to use, copy, modify, and distribute this software
 * and its documentation for NON-COMMERCIAL purposes and without
 * fee is hereby granted provided that this copyright notice
 * appears in all copies. Please refer to the file "copyright.html"
 * for further important copyright and licensing information.
 *
 * SUN MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF
 * THE SOFTWARE, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED
 * TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE, OR NON-INFRINGEMENT. SUN SHALL NOT BE LIABLE FOR
 * ANY DAMAGES SUFFERED BY LICENSEE AS A RESULT OF USING, MODIFYING OR
 * DISTRIBUTING THIS SOFTWARE OR ITS DERIVATIVES.
 *
 */

import java.text.*;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

public class CharacterIteratorTest {
    public CharacterIteratorTest() {
    }

    @Test
    public void TestConstructionAndEquality() {
        String  testText = "Now is the time for all good men to come to the aid of their country.";
        String  testText2 = "Don't bother using this string.";

        CharacterIterator test1 = new StringCharacterIterator(testText);
        CharacterIterator test2 = new StringCharacterIterator(testText, 5);
        CharacterIterator test3 = new StringCharacterIterator(testText, 2, 20, 5);
        CharacterIterator test4 = new StringCharacterIterator(testText2);
        CharacterIterator test5 = (CharacterIterator)test1.clone();

        if (test1.equals(test2) || test1.equals(test3) || test1.equals(test4))
            fail("Construation or equals() failed: Two unequal iterators tested equal");

        if (!test1.equals(test5))
            fail("clone() or equals() failed: Two clones tested unequal");

        if (test1.hashCode() == test2.hashCode() || test1.hashCode() == test3.hashCode()
                        || test1.hashCode() == test4.hashCode())
            fail("hash() failed:  different objects have same hash code");

        if (test1.hashCode() != test5.hashCode())
            fail("hash() failed:  identical objects have different hash codes");

        test1.setIndex(5);
        if (!test1.equals(test2) || test1.equals(test5))
            fail("setIndex() failed");
    }

    @Test
    public void TestIteration() {
        String text = "Now is the time for all good men to come to the aid of their country.";

        CharacterIterator   iter = new StringCharacterIterator(text, 5);

        if (iter.current() != text.charAt(5))
            fail("Iterator didn't start out in the right place.");

        char c = iter.first();
        int     i = 0;

        if (iter.getBeginIndex() != 0 || iter.getEndIndex() != text.length())
            fail("getBeginIndex() or getEndIndex() failed");

        System.out.println("Testing forward iteration...");
        do {
            if (c == CharacterIterator.DONE && i != text.length())
                fail("Iterator reached end prematurely");
            else if (c != text.charAt(i))
                fail("Character mismatch at position " + i + ", iterator has " + c +
                                    ", string has " + text.charAt(c));

            if (iter.current() != c)
                fail("current() isn't working right");
            if (iter.getIndex() != i)
                fail("getIndex() isn't working right");

            if (c != CharacterIterator.DONE) {
                c = iter.next();
                i++;
            }
        } while (c != CharacterIterator.DONE);

        c = iter.last();
        i = text.length() - 1;

        System.out.println("Testing backward iteration...");
        do {
            if (c == CharacterIterator.DONE && i >= 0)
                fail("Iterator reached end prematurely");
            else if (c != text.charAt(i))
                fail("Character mismatch at position " + i + ", iterator has " + c +
                                    ", string has " + text.charAt(c));

            if (iter.current() != c)
                fail("current() isn't working right");
            if (iter.getIndex() != i)
                fail("getIndex() isn't working right");

            if (c != CharacterIterator.DONE) {
                c = iter.previous();
                i--;
            }
        } while (c != CharacterIterator.DONE);

        iter = new StringCharacterIterator(text, 5, 15, 10);
        if (iter.getBeginIndex() != 5 || iter.getEndIndex() != 15)
            fail("creation of a restricted-range iterator failed");

        if (iter.getIndex() != 10 || iter.current() != text.charAt(10))
            fail("starting the iterator in the middle didn't work");

        c = iter.first();
        i = 5;

        System.out.println("Testing forward iteration over a range...");
        do {
            if (c == CharacterIterator.DONE && i != 15)
                fail("Iterator reached end prematurely");
            else if (c != text.charAt(i))
                fail("Character mismatch at position " + i + ", iterator has " + c +
                                    ", string has " + text.charAt(c));

            if (iter.current() != c)
                fail("current() isn't working right");
            if (iter.getIndex() != i)
                fail("getIndex() isn't working right");

            if (c != CharacterIterator.DONE) {
                c = iter.next();
                i++;
            }
        } while (c != CharacterIterator.DONE);

        c = iter.last();
        i = 14;

        System.out.println("Testing backward iteration over a range...");
        do {
            if (c == CharacterIterator.DONE && i >= 5)
                fail("Iterator reached end prematurely");
            else if (c != text.charAt(i))
                fail("Character mismatch at position " + i + ", iterator has " + c +
                                    ", string has " + text.charAt(c));

            if (iter.current() != c)
                fail("current() isn't working right");
            if (iter.getIndex() != i)
                fail("getIndex() isn't working right");

            if (c != CharacterIterator.DONE) {
                c = iter.previous();
                i--;
            }
        } while (c != CharacterIterator.DONE);
    }

    /**
     * @bug 4082050 4078261 4078255
     */
    @Test
    public void TestPathologicalCases() {
        String text = "This is only a test.";

/*
This test is commented out until API-change approval for bug #4082050 goes through.
        CharacterIterator iter = new StringCharacterIterator(text, 5, 5, 5);
        if (iter.first() != CharacterIterator.DONE
            || iter.next() != CharacterIterator.DONE
            || iter.last() != CharacterIterator.DONE
            || iter.previous() != CharacterIterator.DONE
            || iter.current() != CharacterIterator.DONE
            || iter.getIndex() != 5)
            fail("Got something other than DONE when performing operations on an empty StringCharacterIterator");
*/
CharacterIterator iter = null;

        boolean gotException = false;
        try {
            iter = new StringCharacterIterator(text, 5, 100, 5);
        }
        catch (IllegalArgumentException e) {
            gotException = true;
        }
        if (!gotException)
            fail("StringCharacterIterator didn't throw an exception when given an invalid substring range.");

        iter = new StringCharacterIterator(text);
        int expectedIndex = iter.getEndIndex();
        int actualIndex;

        iter.last();
        actualIndex = iter.getIndex();
        if (actualIndex != expectedIndex - 1)
            fail("last() failed: expected " + (expectedIndex - 1) + ", got " + actualIndex);

        iter.next();
        actualIndex = iter.getIndex();
        if (actualIndex != expectedIndex)
            fail("next() after last() failed: expected " + expectedIndex + ", got " + actualIndex);

        iter.next();
        actualIndex = iter.getIndex();
        if (actualIndex != expectedIndex)
            fail("second next() after last() failed: expected " + expectedIndex + ", got " + actualIndex);
    }

    /*
     * @bug 4123771 4051073
     * #4123771 is actually a duplicate of bug #4051073, which was fixed some time ago, but
     * no one ever added a regression test for it.
     */
    @Test
    public void TestBug4123771() {
        String text = "Some string for testing";
        StringCharacterIterator iter = new StringCharacterIterator(text);
        int index = iter.getEndIndex();
        try {
            char c = iter.setIndex(index);
        }
        catch (Exception e) {
            System.out.println("method setIndex(int position) throws unexpected exception " + e);
            System.out.println(" position: " + index);
            System.out.println(" getEndIndex(): " + iter.getEndIndex());
            System.out.println(" text.length(): " + text.length());
            fail(""); 
        }
    }
}
