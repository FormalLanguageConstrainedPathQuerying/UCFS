/*
 * Copyright (c) 2001, 2018, Oracle and/or its affiliates. All rights reserved.
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


package nsk.jdwp.StackFrame.PopFrames;

import nsk.share.*;
import nsk.share.jpda.*;
import nsk.share.jdwp.*;

import java.io.*;

/**
 * This class represents debuggee part in the test.
 */
public class popframes001a {

    public static final String THREAD_NAME = "testedThread";

    public static final int BREAKPOINT_LINE_NUMBER = 113;

    private static volatile ArgumentHandler argumentHandler = null;
    private static volatile Log log = null;

    public static void main(String args[]) {
        popframes001a _popframes001a = new popframes001a();
        System.exit(popframes001.JCK_STATUS_BASE + _popframes001a.runIt(args, System.err));
    }

    public int runIt(String args[], PrintStream out) {
        argumentHandler = new ArgumentHandler(args);
        log = new Log(out, argumentHandler);

        log.display("Creating testing thread");
        TestedThreadClass thread = new TestedThreadClass(THREAD_NAME);
        log.display("  ... thread created");

        log.display("Starting tested thread");
        thread.start();
        log.display("  ... thread started");

        try {
            log.display("Waiting for tested thread finished");
            thread.join();
            log.display("  ... thread finished");
        } catch(InterruptedException e) {
            log.complain("Interruption while waiting for tested thread finished:\n\t" + e);
            log.display("Debugee FAILED");
            return popframes001.FAILED;
        }

        log.display("Debugee PASSED");
        return popframes001.PASSED;
    }

    public static class TestedThreadClass extends Thread {

        public static volatile int invokations = 0;

        public TestedThreadClass(String name) {
            super(name);
        }

        public void run() {
            log.display("Tested thread: started");

            int foo = 100;
            foo = testedMethod(foo);

            log.display("Tested thread: finished");
        }

        public int testedMethod(int arg) {
            invokations++;
            log.display("Tested method invoked " + invokations + " time");
            int boo = 0;

            log.display("Breakpoint line reached");
            boo = arg * 2; 
            log.display("Breakpoint line passed");

            return boo;
        }
    }
}
