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
  @bug 4453162
  @summary MouseAdapter should implement MouseMotionListener and MouseWheelListener
  @author andrei.dmitriev: area=
  @library ../../regtesthelpers
  @build Util
  @run main MouseAdapterUnitTest
*/

import java.awt.*;
import java.awt.event.*;
import test.java.awt.regtesthelpers.Util;

public class MouseAdapterUnitTest
{
    static Point pt;
    static Frame frame = new Frame("Test Frame");
    static Button b = new Button("Test Button");
    static Robot robot;
    static boolean clicked = false;
    static boolean pressed = false;
    static boolean released = false;
    static boolean entered = false;
    static boolean exited = false;
    static boolean rotated = false;
    static boolean dragged = false;
    static boolean moved = false;

    private static void init()
    {
        MouseAdapter ma = new MouseAdapter(){
                public void mouseClicked(MouseEvent e) {clicked = true;}

                public void mousePressed(MouseEvent e) { pressed = true;}

                public void mouseReleased(MouseEvent e) {released = true;}

                public void mouseEntered(MouseEvent e) { entered = true;}

                public void mouseExited(MouseEvent e) {exited  = true;}

                public void mouseWheelMoved(MouseWheelEvent e){rotated = true;}

                public void mouseDragged(MouseEvent e){dragged = true;}

                public void mouseMoved(MouseEvent e){moved = true;}

            };

        b.addMouseListener(ma);
        b.addMouseWheelListener(ma);
        b.addMouseMotionListener(ma);

        frame.add(b);
        frame.pack();
        frame.setVisible(true);

        try{
            robot = new Robot();
            robot.setAutoWaitForIdle(true);
            robot.setAutoDelay(50);

            Util.waitForIdle(robot);

            pt = b.getLocationOnScreen();
            testPressMouseButton(InputEvent.BUTTON1_MASK);
            testDragMouseButton(InputEvent.BUTTON1_MASK);
            testMoveMouseButton();
            testCrossingMouseButton();
            testWheelMouseButton();
        } catch (Throwable e) {
            throw new RuntimeException("Test failed. Exception thrown: "+e);
        }

        MouseAdapterUnitTest.pass();

    }

    public static void testPressMouseButton(int button){
        robot.mouseMove(pt.x + b.getWidth()/2, pt.y + b.getHeight()/2);
        robot.delay(100);
        robot.mousePress(button);
        robot.mouseRelease(button);
        robot.delay(300);


        if ( !pressed || !released || !clicked ){
            dumpListenerState();
            fail("press, release or click hasn't come");
        }
    }

    public static void testWheelMouseButton(){
        robot.mouseMove(pt.x + b.getWidth()/2, pt.y + b.getHeight()/2);
        robot.mouseWheel(10);
        if ( !rotated){
            dumpListenerState();
            fail("Wheel event hasn't come");
        }
    }

    public static void testDragMouseButton(int button) {
        robot.mouseMove(pt.x + b.getWidth()/2, pt.y + b.getHeight()/2);
        robot.mousePress(button);
        moveMouse(pt.x + b.getWidth()/2, pt.y +
                  b.getHeight()/2,
                  pt.x + b.getWidth()/2,
                  pt.y + 2 * b.getHeight());
        robot.mouseRelease(button);

        if ( !dragged){
            dumpListenerState();
            fail("dragged hasn't come");
        }

    }

    public static void testMoveMouseButton() {
        moveMouse(pt.x + b.getWidth()/2, pt.y +
                  b.getHeight()/2,
                  pt.x + b.getWidth()/2,
                  pt.y + 2 * b.getHeight());

        if ( !moved){
            dumpListenerState();
            fail("dragged hasn't come");
        }

    }

    public static void moveMouse(int x0, int y0, int x1, int y1){
        int curX = x0;
        int curY = y0;
        int dx = x0 < x1 ? 1 : -1;
        int dy = y0 < y1 ? 1 : -1;

        while (curX != x1){
            curX += dx;
            robot.mouseMove(curX, curY);
        }
        while (curY != y1 ){
            curY += dy;
            robot.mouseMove(curX, curY);
        }
    }

    public static void testCrossingMouseButton() {
        moveMouse(pt.x + b.getWidth()/2,
                  pt.y + b.getHeight()/2,
                  pt.x + b.getWidth()/2,
                  pt.y + 2 * b.getHeight());
        moveMouse(pt.x + b.getWidth()/2,
                  pt.y + 2 * b.getHeight()/2,
                  pt.x + b.getWidth()/2,
                  pt.y + b.getHeight());

        if ( !entered || !exited){
            dumpListenerState();
            fail("enter or exit hasn't come");
        }

    }

    public static void dumpListenerState(){
        System.out.println("pressed = "+pressed);
        System.out.println("released = "+released);
        System.out.println("clicked = "+clicked);
        System.out.println("entered = "+exited);
        System.out.println("rotated = "+rotated);
        System.out.println("dragged = "+dragged);
        System.out.println("moved = "+moved);
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
