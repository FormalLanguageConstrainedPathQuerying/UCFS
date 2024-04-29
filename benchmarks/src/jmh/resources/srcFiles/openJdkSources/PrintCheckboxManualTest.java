/*
 * Copyright (c) 2004, 2007, Oracle and/or its affiliates. All rights reserved.
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
  @bug 5045936 5055171
  @summary Tests that there is no ClassCastException thrown in printing
   checkbox and scrollbar with XAWT
  @key printer
  @run applet/manual=yesno PrintCheckboxManualTest.html
*/




import java.awt.*;
import java.awt.event.*;




public class PrintCheckboxManualTest extends Panel
{
    Frame f;

    public static void main(String[] args) {
        PrintCheckboxManualTest a = new PrintCheckboxManualTest();

        a.init();
        a.start();
    }

    public void init()
    {
        this.setLayout (new BorderLayout ());

        String[] instructions =
        {
            "Linux or Solaris with XToolkit ONLY!",
            "1. Click the 'Print' button on the frame",
            "2. Select a printer in the print dialog and proceed",
            "3. If the frame with checkbox and button on it is printed successfully test PASSED else FAILED"
        };
        Sysout.createDialogWithInstructions( instructions );

    }

    public void start ()
    {
        setSize (200,200);
        setVisible(true);
        validate();


        f = new Frame("Print checkbox");
        f.setLayout(new GridLayout(2, 2));
        f.setSize(200, 100);

        Checkbox ch = new Checkbox("123");
        ch.setState(true);
        f.add(ch);

        Scrollbar sb = new Scrollbar(Scrollbar.HORIZONTAL);
        f.add(sb);

        Button b = new Button("Print");
        b.addActionListener(new ActionListener()
        {
        public void actionPerformed(ActionEvent ev)
        {
                PrintJob pj = Toolkit.getDefaultToolkit().getPrintJob(f, "PrintCheckboxManualTest", null);
                if (pj != null)
                {
                        try
                        {
                                Graphics g = pj.getGraphics();
                                f.printAll(g);
                                g.dispose();
                                pj.end();
                                Sysout.println("Test PASSED");
                        }
                        catch (ClassCastException cce)
                        {
                                Sysout.println("Test FAILED: ClassCastException");
                        }
                        catch (Exception e)
                        {
                                Sysout.println("Test FAILED: unknown Exception");
                        }
                }
        }
        });
        f.add(b);

        f.setVisible(true);
    }



}

/* Place other classes related to the test after this line */





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
        dialog.setVisible(true);
        println( "Any messages for the tester will display here." );
    }

    public static void createDialog( )
    {
        dialog = new TestDialog( new Frame(), "Instructions" );
        String[] defInstr = { "Instructions will appear here. ", "" } ;
        dialog.printInstructions( defInstr );
        dialog.setVisible(true);
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
class TestDialog extends Dialog
{

    TextArea instructionsText;
    TextArea messageText;
    int maxStringLength = 80;

    public TestDialog( Frame frame, String name )
    {
        super( frame, name );
        int scrollBoth = TextArea.SCROLLBARS_BOTH;
        instructionsText = new TextArea( "", 15, maxStringLength, scrollBoth );
        add( "North", instructionsText );

        messageText = new TextArea( "", 5, maxStringLength, scrollBoth );
        add("Center", messageText);

        pack();

        setVisible(true);
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
        System.out.println(messageIn);
    }

}
