/*
 * Copyright (c) 2006, 2018, Oracle and/or its affiliates. All rights reserved.
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
  @bug 6390103
  @summary Non-Focusable choice throws exception when selecting an item, Win32
  @author andrei.dmitriev area=awt.choice
  @run main UnfocusableCB_ERR
*/

import java.awt.*;
import java.awt.event.*;

public class UnfocusableCB_ERR
{
    static final int delay = 100;
    static Frame frame = new Frame("Test Frame");
    static Choice choice1 = new Choice();
    static Button button = new Button("Test");

    static Robot robot;
    static Point pt;
    static String failed = "";

    private static void init()
    {
        EventQueue.invokeLater(new Runnable() {
            public void run() {
                Thread.currentThread().setUncaughtExceptionHandler(
                   new Thread.UncaughtExceptionHandler(){
                       public void uncaughtException(Thread t, Throwable exc){
                           failed = exc.toString();
                       }
                   });
            }
        });

        frame.setLayout (new FlowLayout ());
        for (int i = 1; i<10;i++){
            choice1.add("item "+i);
        }
        frame.add(button);
        frame.add(choice1);

        choice1.setFocusable(false);

        frame.pack();
        frame.setVisible(true);
        frame.validate();

        try {
            robot = new Robot();
            robot.setAutoDelay(50);
            robot.waitForIdle();
            testSpacePress();
        } catch (Throwable e) {
            UnfocusableCB_ERR.fail("Test failed. Exception thrown: "+e);
        }
        if (failed.equals("")){
            UnfocusableCB_ERR.pass();
        } else {
            UnfocusableCB_ERR.fail("Test failed:");
        }
    }

    public static void testSpacePress(){

        pt = choice1.getLocationOnScreen();
        robot.mouseMove(pt.x + choice1.getWidth()/2, pt.y + choice1.getHeight()/2);
        robot.waitForIdle();
        robot.mousePress(InputEvent.BUTTON1_MASK);
        robot.waitForIdle();
        robot.mouseRelease(InputEvent.BUTTON1_MASK);


        robot.waitForIdle();

        robot.mouseMove(pt.x + choice1.getWidth()/2, pt.y + 2 * choice1.getHeight());
        robot.waitForIdle();

        robot.mouseMove(pt.x + choice1.getWidth()/2, pt.y - choice1.getHeight());
        robot.waitForIdle();

        robot.keyPress(KeyEvent.VK_SPACE);
        robot.keyRelease(KeyEvent.VK_SPACE);
        robot.waitForIdle();
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
