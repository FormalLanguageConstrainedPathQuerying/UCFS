/*
 * Copyright (c) 2001, 2021, Oracle and/or its affiliates. All rights reserved.
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

package nsk.jdi.EventRequestManager.stepRequests;

import nsk.share.*;
import nsk.share.jdi.*;

/**
 * This class is used as debuggee application for the stepreq002 JDI test.
 */

public class stepreq002a {


    static final int PASSED = 0;
    static final int FAILED = 2;
    static final int PASS_BASE = 95;

    static ArgumentHandler argHandler;
    static Log log;


    public static void log1(String message) {
        log.display("**> debuggee: " + message);
    }

    private static void logErr(String message) {
        log.complain("**> debuggee: " + message);
    }


    static Thread testField[] = new Thread[10];


    static int exitCode = PASSED;

    static int instruction = 1;
    static int end         = 0;
    static int maxInstr    = 1;    

    static int lineForComm = 2;

    private static void methodForCommunication() {
        int i1 = instruction;
        int i2 = i1;
        int i3 = i2;
    }

    public static void main (String argv[]) {

        argHandler = new ArgumentHandler(argv);
        log = argHandler.createDebugeeLog();

        log1("debuggee started!");

        label0:
            for (int i = 0; ; i++) {

                if (instruction > maxInstr) {
                    logErr("ERROR: unexpected instruction: " + instruction);
                    exitCode = FAILED;
                    break ;
                }

                switch (i) {


                    case 0:
                            synchronized (lockObj1) {
                                for (int ii = 0; ii < 10; ii++) {
                                    testField[ii] = JDIThreadFactory.newThread(new Thread1stepreq002a("thread" + ii));
                                    threadStart(testField[ii]);
                                }
                                methodForCommunication();
                            }
                            break ;


                    default:
                                instruction = end;
                                methodForCommunication();
                                break label0;
                }
            }

        log1("debuggee exits");
        System.exit(exitCode + PASS_BASE);
    }

    static Object lockObj1      = new Object();
    static Object waitnotifyObj = new Object();

    static int threadStart(Thread t) {
        synchronized (waitnotifyObj) {
            t.start();
            try {
                waitnotifyObj.wait();
            } catch ( Exception e) {
                exitCode = FAILED;
                logErr("       Exception : " + e );
                return FAILED;
            }
        }
        return PASSED;
    }

}

class Thread1stepreq002a extends NamedTask {

    public Thread1stepreq002a(String threadName) {
        super(threadName);
    }

    public void run() {
        stepreq002a.log1("  'run': enter  :: threadName == " + getName());
        synchronized(stepreq002a.waitnotifyObj) {
            stepreq002a.waitnotifyObj.notify();
        }
        synchronized(stepreq002a.lockObj1) {
            stepreq002a.log1("  'run': exit   :: threadName == " + getName());
        }
        return;
    }
}
