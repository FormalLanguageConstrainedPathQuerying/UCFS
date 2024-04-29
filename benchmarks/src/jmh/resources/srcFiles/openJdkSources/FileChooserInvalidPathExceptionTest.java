/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.BorderLayout;

import javax.swing.JFrame;
import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import java.lang.reflect.InvocationTargetException;

/*
 * @test
 * @bug 8307105
 * @key headful
 * @requires (os.family == "windows")
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @summary Test to check if the certain windows like "This PC",
 * "Library", "Network" does not throw Invalid Path Exception on selection.
 * @run main/manual FileChooserInvalidPathExceptionTest
 */

public class FileChooserInvalidPathExceptionTest {
    static JFrame frame;
    static JFileChooser jfc;
    static PassFailJFrame passFailJFrame;

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                initialize();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        passFailJFrame.awaitAndCheck();
    }

    static void initialize() throws Exception {
        final String INSTRUCTIONS = """
                Instructions to Test:
                1. Navigate to any windows specific folders like My PC/Libraries/
                Network.
                2. Select and traverse through those folders.
                3. On click of the mentioned folder if InvalidPathException
                occurs does not occur then test is PASS.
                """;
        frame = new JFrame("JFileChooser IPE test");
        jfc = new JFileChooser();
        passFailJFrame = new PassFailJFrame("Test Instructions", INSTRUCTIONS, 5L, 8, 40);

        PassFailJFrame.addTestWindow(frame);
        PassFailJFrame.positionTestWindow(frame, PassFailJFrame.Position.HORIZONTAL);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        jfc.setControlButtonsAreShown(false);
        jfc.setDialogType(JFileChooser.CUSTOM_DIALOG);

        frame.add(jfc, BorderLayout.CENTER);
        frame.pack();
        frame.setVisible(true);
    }
}
