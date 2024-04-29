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

package nsk.jdwp.StackFrame.ThisObject;

import nsk.share.*;
import nsk.share.jpda.*;
import nsk.share.jdwp.*;

import java.io.*;

/**
 * This class represents debuggee part in the test.
 */
public class thisobject001a {

    public static final String OBJECT_CLASS_NAME = "TestedObjectClass";
    public static final String THREAD_CLASS_NAME = "TestedThreadClass";
    public static final String THREAD_NAME = "TestedThreadName";

    public static final String THREAD_FIELD_NAME = "thread";
    public static final String OBJECT_FIELD_NAME = "object";

    private static Object threadReady = new Object();
    private static Object threadLock = new Object();

    private static volatile ArgumentHandler argumentHandler = null;
    private static volatile Log log = null;

    public static void main(String args[]) {
        thisobject001a _thisobject001a = new thisobject001a();
        System.exit(thisobject001.JCK_STATUS_BASE + _thisobject001a.runIt(args, System.err));
    }

    public int runIt(String args[], PrintStream out) {
        argumentHandler = new ArgumentHandler(args);
        log = new Log(out, argumentHandler);

        log.display("Creating pipe");
        IOPipe pipe = argumentHandler.createDebugeeIOPipe(log);

        synchronized (threadLock) {

            log.display("Creating object of tested class");
            TestedObjectClass.object = new TestedObjectClass();
            log.display("Creating tested thread");
            TestedObjectClass.thread = new TestedThreadClass(THREAD_NAME);

            synchronized (threadReady) {
                TestedObjectClass.thread.start();
                try {
                    threadReady.wait();
                    log.display("Sending signal to debugger: " + thisobject001.READY);
                    pipe.println(thisobject001.READY);
                } catch (InterruptedException e) {
                    log.complain("Interruption while waiting for thread started: " + e);
                    pipe.println(thisobject001.ERROR);
                }
            }

            log.display("Waiting for signal from debugger: " + thisobject001.QUIT);
            String signal = pipe.readln();
            log.display("Received signal from debugger: " + signal);

            if (signal == null || !signal.equals(thisobject001.QUIT)) {
                log.complain("Unexpected communication signal from debugee: " + signal
                            + " (expected: " + thisobject001.QUIT + ")");
                log.complain("Debugee FAILED");
                return thisobject001.FAILED;
            }

        }

        try {
            log.display("Waiting for tested thread finished");
            TestedObjectClass.thread.join();
        } catch (InterruptedException e) {
            log.complain("Interruption while waiting for tested thread finished:\n\t"
                        + e);
            log.complain("Debugee FAILED");
            return thisobject001.FAILED;
        }

        log.display("Debugee PASSED");
        return thisobject001.PASSED;
    }

    public static class TestedThreadClass extends Thread {

        TestedThreadClass(String name) {
            super(name);
        }

        public void run() {
            log.display("Tested thread started");

            int foo = 100;
            TestedObjectClass.object.testedMethod(foo);

            log.display("Tested thread finished");
        }

    }

    public static class TestedObjectClass {

        public static volatile TestedThreadClass thread = null;
        public static volatile TestedObjectClass object = null;

        public void testedMethod(int foo) {
            log.display("Tested frame entered");

            int boo = foo;

            synchronized (threadReady) {
                threadReady.notifyAll();
            }

            synchronized (threadLock) {
                log.display("Tested frame dropped");
            }
        }

    }

}
