/*
 * Copyright (c) 2007, 2018, Oracle and/or its affiliates. All rights reserved.
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
  @test
  @key headful
  @bug 4811096 8173409
  @summary Tests whether opaque and non-opaque components mix correctly
  @author anthony.petrov@...: area=awt.mixing
  @library ../regtesthelpers
  @build Util
  @run main OpaqueTest
*/


/**
 * OpaqueTest.java
 *
 * summary:  OpaqueTest
 */

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import test.java.awt.regtesthelpers.Util;



public class OpaqueTest
{


    static String testSeq = new String("");
    final static String checkSeq = new String("010000101");

    private static void init()
    {
        final Frame f = new Frame("Button-JButton mix test");
        final Panel p = new Panel();
        final Button heavy = new Button("  Heavyweight Button  ");
        final JButton light = new JButton("  LW Button  ");

        heavy.addActionListener(new java.awt.event.ActionListener()
                {
                    public void actionPerformed(java.awt.event.ActionEvent e) {
                        p.setComponentZOrder(light, 0);
                        f.validate();
                        testSeq = testSeq + "0";
                    }
                }
                );

        light.addActionListener(new java.awt.event.ActionListener()
                {
                    public void actionPerformed(java.awt.event.ActionEvent e) {
                        p.setComponentZOrder(heavy, 0);
                        f.validate();
                        testSeq = testSeq + "1";
                    }
                }
                );

        heavy.setBounds(30, 30, 200, 200);
        light.setBounds(10, 10, 50, 50);

        p.setLayout(null);
        p.add(heavy);
        p.add(light);
        f.add(p);
        f.setBounds(50, 50, 400, 400);
        f.show();


        Robot robot = Util.createRobot();
        robot.setAutoDelay(20);

        Util.waitForIdle(robot);

        Point heavyLoc = heavy.getLocationOnScreen();
        robot.mouseMove(heavyLoc.x + 5, heavyLoc.y + 5);

        for (int i = 0; i < 9; ++i) {
            if (i == 3) {
                light.setMixingCutoutShape(new Rectangle());
            }
            if (i == 6) {
                light.setMixingCutoutShape(null);
            }

            robot.mousePress(InputEvent.BUTTON1_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_MASK);
            Util.waitForIdle(robot);
        }

        Util.waitForIdle(robot);

        if (testSeq.equals(checkSeq)) {
            OpaqueTest.pass();
        } else {
            OpaqueTest.fail("The components changed their visible Z-order in a wrong sequence: '" + testSeq + "' instead of '" + checkSeq + "'");
        }
    }



    /*****************************************************
     * Standard Test Machinery Section
     * DO NOT modify anything in this section -- it's a
     * standard chunk of code which has all of the
     * synchronisation necessary for the test harness.
     * By keeping it the same in all tests, it is easier
     * to read and understand someone else's test, as
     * well as insuring that all tests behave correctly
     * with the test harness.
     * There is a section following this for test-
     * classes
     ******************************************************/
    private static boolean theTestPassed = false;
    private static boolean testGeneratedInterrupt = false;
    private static String failureMessage = "";

    private static Thread mainThread = null;

    private static int sleepTime = 300000;

    public static void main( String args[] ) throws InterruptedException
    {
        mainThread = Thread.currentThread();
        try
        {
            init();
        }
        catch( TestPassedException e )
        {
            return;
        }

        try
        {
            Thread.sleep( sleepTime );
            throw new RuntimeException( "Timed out after " + sleepTime/1000 + " seconds" );
        }
        catch (InterruptedException e)
        {
            if( ! testGeneratedInterrupt ) throw e;

            testGeneratedInterrupt = false;

            if ( theTestPassed == false )
            {
                throw new RuntimeException( failureMessage );
            }
        }

    }

    public static synchronized void setTimeoutTo( int seconds )
    {
        sleepTime = seconds * 1000;
    }

    public static synchronized void pass()
    {
        System.out.println( "The test passed." );
        System.out.println( "The test is over, hit  Ctl-C to stop Java VM" );
        if ( mainThread == Thread.currentThread() )
        {
            theTestPassed = true;
            throw new TestPassedException();
        }
        theTestPassed = true;
        testGeneratedInterrupt = true;
        mainThread.interrupt();
    }

    public static synchronized void fail()
    {
        fail( "it just plain failed! :-)" );
    }

    public static synchronized void fail( String whyFailed )
    {
        System.out.println( "The test failed: " + whyFailed );
        System.out.println( "The test is over, hit  Ctl-C to stop Java VM" );
        if ( mainThread == Thread.currentThread() )
        {
            throw new RuntimeException( whyFailed );
        }
        theTestPassed = false;
        testGeneratedInterrupt = true;
        failureMessage = whyFailed;
        mainThread.interrupt();
    }

}

class TestPassedException extends RuntimeException
{
}
