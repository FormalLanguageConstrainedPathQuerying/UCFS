/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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
  @bug 6852592
  @summary invalidate() must stop when it encounters a validate root
  @author anthony.petrov@sun.com
  @run main/othervm -Djava.awt.smartInvalidate=true InvalidateMustRespectValidateRoots
*/

import javax.swing.*;
import java.awt.event.*;

public class InvalidateMustRespectValidateRoots {
    private static volatile JRootPane rootPane;

    public static void main(String args[]) throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                JFrame frame = new JFrame();
                final JButton button = new JButton();

                frame.add(button);

                button.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent ev) {
                        if (button.isValid()) {
                            button.invalidate();
                        } else {
                            button.revalidate();
                        }
                    }
                });

                rootPane = frame.getRootPane();


                frame.pack(); 
                frame.setVisible(true);

                if (!frame.isValid()) {
                    throw new RuntimeException(
                            "setVisible(true) failed to validate the frame");
                }

                button.invalidate();

                if (rootPane.isValid()) {
                    throw new RuntimeException(
                            "invalidate() failed to invalidate the root pane");
                }

                if (!frame.isValid()) {
                    throw new RuntimeException(
                            "invalidate() invalidated the frame");
                }

                button.revalidate();

            }
        });

        Thread.sleep(1000);

        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                if (!rootPane.isValid()) {
                    throw new RuntimeException(
                            "revalidate() failed to validate the hierarchy");
                }
            }
        });
    }
}
