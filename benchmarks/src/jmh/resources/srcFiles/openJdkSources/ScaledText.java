/*
 * Copyright (c) 2007, Oracle and/or its affiliates. All rights reserved.
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
  @bug 4291373
  @summary  Printing of scaled text is wrong / disappearing
  @key printer
  @run main/manual ScaledText
*/
import java.awt.*;
import java.awt.event.*;
import java.awt.print.*;


public class ScaledText implements Printable {

   private Image opaqueimg,transimg;

   private static void init() {


        String[] instructions = {
         "On-screen inspection is not possible for this printing-specific",
         "test therefore its only output is two printed pages.",
         "To be able to run this test it is required to have a default",
         "printer configured in your user environment.",
         "",
         "Visual inspection of the printed pages is needed. A passing",
         "test will print a page on which 6 lines of text will be",
         "printed, all of the same size. The test fails only if the sizes",
         "are different, or not all of the sizes labelled 1.0, 2.0, 4.0",
         "8.0, 16.0, 32.0 appear."
       };

      Sysout.createDialog( );
      Sysout.printInstructions( instructions );

        PrinterJob pjob = PrinterJob.getPrinterJob();

        Book book = new Book();

        PageFormat portrait = pjob.defaultPage();
        book.append(new ScaledText(),portrait);

        pjob.setPageable(book);

        if (pjob.printDialog()) {
            try {
                pjob.print();
            } catch (PrinterException e) {
                System.err.println(e);
                e.printStackTrace();
            }
        }
        System.out.println("Done Printing");

    }


  public ScaledText() {
  }

  public int print(Graphics g, PageFormat pgFmt, int pgIndex) {

       Graphics2D g2D = (Graphics2D) g;
       g2D.translate(pgFmt.getImageableX(), pgFmt.getImageableY());

       g2D.setColor(Color.black);
       Font font = new Font("serif", Font.PLAIN, 1);

       float scale;
       float x;
       float y;

       scale = 1.0f;
       x     = 3.0f;
       y     = 3.0f;
       printScale(g2D, font, scale, x, y);

       scale = 2.0f;
       x     = 3.0f;
       y     = 3.5f;
       printScale(g2D, font, scale, x, y);

       scale = 4.0f;
       x     = 3.0f;
       y     = 4.0f;
       printScale(g2D, font, scale, x, y);

       scale = 8.0f;
       x     = 3.0f;
       y     = 4.5f;
       printScale(g2D, font, scale, x, y);

       scale = 16.0f;
       x     = 3.0f;
       y     = 5.0f;
       printScale(g2D, font, scale, x, y);

       scale = 32.0f;
       x     = 3.0f;
       y     = 5.5f;
       printScale(g2D, font, scale, x, y);

       return Printable.PAGE_EXISTS;
  }

  /**
   * The graphics is scaled and the font and the positions
   * are reduced in respect to the scaling, so that all
   * printing should be the same.
   *
   * @param g2D     graphics2D to paint on
   * @param font    font to paint
   * @param scale   scale for the painting
   * @param x       x position
   * @param y       y position
   */
  private void printScale(Graphics2D g2D, Font font,
                           float scale, float x, float y) {

    int RES = 72;

    g2D.scale(scale, scale);

    g2D.setFont   (font.deriveFont(10.0f / scale));
    g2D.drawString("This text is scaled by a factor of " + scale,
                   x * RES / scale, y * RES / scale);

    g2D.scale(1/scale, 1/scale);

}

   /*****************************************************
     Standard Test Machinery Section
      DO NOT modify anything in this section -- it's a
      standard chunk of code which has all of the
      synchronisation necessary for the test harness.
      By keeping it the same in all tests, it is easier
      to read and understand someone else's test, as
      well as insuring that all tests behave correctly
      with the test harness.
     There is a section following this for test-defined
      classes
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
      Sysout.println( "The test passed." );
      Sysout.println( "The test is over, hit  Ctl-C to stop Java VM" );
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
    }

   public static synchronized void fail( String whyFailed )
    {
      Sysout.println( "The test failed: " + whyFailed );
      Sysout.println( "The test is over, hit  Ctl-C to stop Java VM" );
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






/****************************************************
 Standard Test Machinery
 DO NOT modify anything below -- it's a standard
  chunk of code whose purpose is to make user
  interaction uniform, and thereby make it simpler
  to read and understand someone else's test.
 ****************************************************/

/**
 This is part of the standard test machinery.
 It creates a dialog (with the instructions), and is the interface
  for sending text messages to the user.
 To print the instructions, send an array of strings to Sysout.createDialog
  WithInstructions method.  Put one line of instructions per array entry.
 To display a message for the tester to see, simply call Sysout.println
  with the string to be displayed.
 This mimics System.out.println but works within the test harness as well
  as standalone.
 */

class Sysout
 {
   private static TestDialog dialog;

   public static void createDialogWithInstructions( String[] instructions )
    {
      dialog = new TestDialog( new Frame(), "Instructions" );
      dialog.printInstructions( instructions );
      dialog.show();
      println( "Any messages for the tester will display here." );
    }

   public static void createDialog( )
    {
      dialog = new TestDialog( new Frame(), "Instructions" );
      String[] defInstr = { "Instructions will appear here. ", "" } ;
      dialog.printInstructions( defInstr );
      dialog.show();
      println( "Any messages for the tester will display here." );
    }


   public static void printInstructions( String[] instructions )
    {
      dialog.printInstructions( instructions );
    }


   public static void println( String messageIn )
    {
      dialog.displayMessage( messageIn );
    }

 }

/**
  This is part of the standard test machinery.  It provides a place for the
   test instructions to be displayed, and a place for interactive messages
   to the user to be displayed.
  To have the test instructions displayed, see Sysout.
  To have a message to the user be displayed, see Sysout.
  Do not call anything in this dialog directly.
  */
class TestDialog extends Dialog implements ActionListener {

   TextArea instructionsText;
   TextArea messageText;
   int maxStringLength = 80;
   Panel  buttonP = new Panel();
   Button passB = new Button( "pass" );
   Button failB = new Button( "fail" );

   public TestDialog( Frame frame, String name )
    {
      super( frame, name );
      int scrollBoth = TextArea.SCROLLBARS_BOTH;
      instructionsText = new TextArea( "", 15, maxStringLength, scrollBoth );
      add( "North", instructionsText );

      messageText = new TextArea( "", 5, maxStringLength, scrollBoth );
      add("Center", messageText);

      passB = new Button( "pass" );
      passB.setActionCommand( "pass" );
      passB.addActionListener( this );
      buttonP.add( "East", passB );

      failB = new Button( "fail" );
      failB.setActionCommand( "fail" );
      failB.addActionListener( this );
      buttonP.add( "West", failB );

      add( "South", buttonP );
      pack();

      show();
    }

   public void printInstructions( String[] instructions )
    {
      instructionsText.setText( "" );


      String printStr, remainingStr;
      for( int i=0; i < instructions.length; i++ )
       {
         remainingStr = instructions[ i ];
         while( remainingStr.length() > 0 )
          {
            if( remainingStr.length() >= maxStringLength )
             {
               int posOfSpace = remainingStr.
                  lastIndexOf( ' ', maxStringLength - 1 );

               if( posOfSpace <= 0 ) posOfSpace = maxStringLength - 1;

               printStr = remainingStr.substring( 0, posOfSpace + 1 );
               remainingStr = remainingStr.substring( posOfSpace + 1 );
             }
            else
             {
               printStr = remainingStr;
               remainingStr = "";
             }

            instructionsText.append( printStr + "\n" );

          }

       }

    }

   public void displayMessage( String messageIn )
    {
      messageText.append( messageIn + "\n" );
    }

   public void actionPerformed( ActionEvent e )
    {
      if( e.getActionCommand() == "pass" )
       {
         ScaledText.pass();
       }
      else
       {
         ScaledText.fail();
       }
    }

 }
