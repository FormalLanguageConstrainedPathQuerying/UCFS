/*
 * Copyright (c) 2014, 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Frame;
import java.awt.Panel;
import java.awt.event.MouseWheelEvent;
import javax.swing.SwingUtilities;

/**
 * @test
 * @key headful
 * @bug 8049533
 * @summary SwingUtilities.convertMouseEvent misses
 *      MouseWheelEvent.preciseWheelRotation
 * @run main bug8049533
 */
public class bug8049533 {

    private static final double PRECISE_WHEEL_ROTATION = 3.14;

    public static void main(String[] args) {
        Frame frame = new Frame();
        Panel panel = new Panel();
        frame.add(panel);

        MouseWheelEvent event = new MouseWheelEvent(panel,
                0, 0, 0, 0, 0, 0, 0, 0, false, 0, 0,
                2, 
                PRECISE_WHEEL_ROTATION); 

        MouseWheelEvent convertedEvent = (MouseWheelEvent) SwingUtilities.
                convertMouseEvent(event.getComponent(), event, null);

        if (convertedEvent.getPreciseWheelRotation() != PRECISE_WHEEL_ROTATION) {
            throw new RuntimeException("PreciseWheelRotation field is not copied!");
        }
    }
}
