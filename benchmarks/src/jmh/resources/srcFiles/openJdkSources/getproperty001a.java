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

package nsk.jdi.EventRequest.getProperty;

import nsk.share.*;
import nsk.share.jdi.*;

/**
 * This class is used as debuggee application for the getproperty001 JDI test.
 */

public class getproperty001a {


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


    static Thread thread1 = null;

    static TestClass11 obj = new TestClass11();

    static NullPointerException excObj = new NullPointerException();


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
                            thread1 = JDIThreadFactory.newThread(new Thread1getproperty001a("thread1"));

                            synchronized (lockObj) {
                                threadStart(thread1);
                                log1("methodForCommunication();----");
                                methodForCommunication();
                            }
                            i++;

                    case 1:
                            break;
                    case 2:
                            break;
                    case 3:
                            break;
                    case 4:
                            break;
                    case 5:
                            break;
                    case 6:
                            break;
                    case 7:
                            break;
                    case 8:
                            break;
                    case 9:
                            break;
                    case 10:
                            break;
                    case 11:
                            break;


                    default:
                                instruction = end;
                                break;
                }


                log1("methodForCommunication();");
                methodForCommunication();
                if (instruction == end)
                    break;


            }

        log1("debuggee exits");
        System.exit(exitCode + PASS_BASE);
    }

    static Object lockObj       = new Object();
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

class TestClass10{

    static int var101 = 0;
    static int var102 = 0;
    static int var103 = 0;

    static void method10 () {
        getproperty001a.log1("entered: method10");
        var101 = 1;
        var103 = var101;
        var102 = var103;

    }
}
class TestClass11 extends TestClass10{

    static int var111 = 0;
    static int var112 = 0;
    static int var113 = 0;

    static void method11 () {
        getproperty001a.log1("entered: method11");
        var101 = 1;
        var103 = var101;
        var102 = var103;

        var111 = 1;
        var113 = var111;
        var112 = var113;
    }
}

class Thread1getproperty001a extends NamedTask {

    public Thread1getproperty001a(String threadName) {
        super(threadName);
    }

    public void run() {
        getproperty001a.log1("  'run': enter  :: threadName == " + getName());
        synchronized(getproperty001a.waitnotifyObj) {
            getproperty001a.waitnotifyObj.notify();
        }
        synchronized(getproperty001a.lockObj) {
            TestClass11.method11();
        }
        getproperty001a.log1("  'run': exit   :: threadName == " + getName());
        return;
    }
}
