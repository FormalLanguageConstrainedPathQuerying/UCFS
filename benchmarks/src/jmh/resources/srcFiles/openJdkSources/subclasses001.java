/*
 * Copyright (c) 2001, 2024, Oracle and/or its affiliates. All rights reserved.
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

package nsk.jdi.ClassType.subclasses;

import nsk.share.*;
import nsk.share.jpda.*;
import nsk.share.jdi.*;

import com.sun.jdi.*;
import java.util.*;
import java.io.*;

/**
 * The test for the implementation of an object of the type     <BR>
 * ClassType.                                                   <BR>
 *                                                              <BR>
 * The test checks up that results of the method                <BR>
 * <code>com.sun.jdi.ClassType.subclasses()</code>              <BR>
 * complies with its spec.                                      <BR>
 * <BR>
 * The cases for testing are as follows.        <BR>
 *                                              <BR>
 * 1)    Class2 extends Class1                  <BR>
 *       hence Class1 has only one subclass     <BR>
 * 2)    Class3 and Class4 extend Class2        <BR>
 *       hence Class2 has two subclasses        <BR>
 * 3)    Class3 has no subclasses               <BR>
 * <BR>
 */

public class subclasses001 {

    static final int PASSED = 0;
    static final int FAILED = 2;
    static final int PASS_BASE = 95;

    static final String
    sHeader1 = "\n==> nsk/jdi/ClassType/subclasses/subclasses001",
    sHeader2 = "--> subclasses001: ",
    sHeader3 = "##> subclasses001: ";


    public static void main (String argv[]) {
        int result = run(argv, System.out);
        if (result != 0) {
            throw new RuntimeException("TEST FAILED with result " + result);
        }
    }

    public static int run (String argv[], PrintStream out) {
        return new subclasses001().runThis(argv, out);
    }



    private static Log  logHandler;

    private static void log1(String message) {
        logHandler.display(sHeader1 + message);
    }
    private static void log2(String message) {
        logHandler.display(sHeader2 + message);
    }
    private static void log3(String message) {
        logHandler.complain(sHeader3 + message);
    }


    private String debuggeeName =
        "nsk.jdi.ClassType.subclasses.subclasses001a";

    String mName = "nsk.jdi.ClassType.subclasses";


    static ArgumentHandler      argsHandler;
    static int                  testExitCode = PASSED;


    private int runThis (String argv[], PrintStream out) {

        Debugee debuggee;

        argsHandler     = new ArgumentHandler(argv);
        logHandler      = new Log(out, argsHandler);
        Binder binder   = new Binder(argsHandler, logHandler);

        if (argsHandler.verbose()) {
            debuggee = binder.bindToDebugee(debuggeeName + " -vbs");  
        } else {
            debuggee = binder.bindToDebugee(debuggeeName);            
        }

        IOPipe pipe     = new IOPipe(debuggee);

        debuggee.redirectStderr(out);
        log2("subclasses001a debuggee launched");
        debuggee.resume();

        String line = pipe.readln();
        if ((line == null) || !line.equals("ready")) {
            log3("signal received is not 'ready' but: " + line);
            return FAILED;
        } else {
            log2("'ready' recieved");
        }

        VirtualMachine vm = debuggee.VM();

        log1("      TESTING BEGINS");

        for (int i = 0; ; i++) {
        pipe.println("newcheck");
            line = pipe.readln();

            if (line.equals("checkend")) {
                log2("     : returned string is 'checkend'");
                break ;
            } else if (!line.equals("checkready")) {
                log3("ERROR: returned string is not 'checkready'");
                testExitCode = FAILED;
                break ;
            }

            log1("new check: #" + i);


            ReferenceType classRefType = null;
            List          l            = null;
            String        name         = null;
            int i2;

            for (i2 = 0; ; i2++) {

                int expresult = 0;

                log2("new check: #" + i2);

                switch (i2) {

                case 0:         

                        List list1         = vm.classesByName(mName + ".Class1ForCheck");
                             classRefType  = (ReferenceType) list1.get(0);
                        List subClass1list = ((ClassType) classRefType).subclasses();

                        if (subClass1list.size() != 1) {
                            log3("ERROR : subClass1list.size() != 1 in case: Class2 extends Class1");
                            expresult = 1;
                            break;
                        }

                        ClassType classType2  =
                            (ClassType) (ReferenceType) subClass1list.get(0);
                        name = classType2.name();
                        if (!name.equals(mName + ".Class2ForCheck")) {
                            log3("ERROR : !name.equals('.Class2..' in Class2 extends Class1");
                            expresult = 1;
                            break;
                        }

                        break;

                case 1:         

                        List list2         = vm.classesByName(mName + ".Class2ForCheck");
                             classRefType  = (ReferenceType) list2.get(0);
                        List subClass2list = ((ClassType) classRefType).subclasses();

                        if (subClass2list.size() != 2) {
                            log3("ERROR : subClass2list.size() != 2 in case: Classes 3,4 extend Class2");
                            expresult = 1;
                            break;
                        }

                        ClassType classType3  =
                            (ClassType) (ReferenceType) subClass2list.get(0);
                        name = classType3.name();
                        if (!name.equals(mName + ".Class3ForCheck")) {
                            if (!name.equals(mName + ".Class4ForCheck")) {
                                log3("ERROR : !name.equals('.Class3..' or '.Class4..' in Class3,4 extends Class2");
                                expresult = 1;
                                break;
                            }
                        }
                        classType3  =
                            (ClassType) (ReferenceType) subClass2list.get(1);
                        name = classType3.name();
                        if (!name.equals(mName + ".Class4ForCheck")) {
                            if (!name.equals(mName + ".Class3ForCheck")) {
                                log3("ERROR : !name.equals('.Class4..' or '.Class3..' in Class3,4 extends Class2");
                                expresult = 1;
                                break;
                            }
                        }

                        break;

                case 2:         

                        List list3         = vm.classesByName(mName + ".Class3ForCheck");
                             classRefType  = (ReferenceType) list3.get(0);
                        List subClass3list = ((ClassType) classRefType).subclasses();

                        if (subClass3list.size() != 0) {
                            log3("ERROR : subClass1list.size() != 0 in case: Class3");
                            expresult = 1;
                            break;
                        }

                        break;


                default: expresult = 2;
                         break ;
                }

                if (expresult == 2) {
                    log2("      test cases finished");
                    break ;
                } else if (expresult == 1) {
                    log3("ERROR: expresult != true;  check # = " + i);
                    testExitCode = FAILED;
                }
            }
        }
        log1("      TESTING ENDS");


        pipe.println("quit");
        log2("waiting for the debuggee to finish ...");
        debuggee.waitFor();

        int status = debuggee.getStatus();
        if (status != PASSED + PASS_BASE) {
            log3("debuggee returned UNEXPECTED exit status: " +
                    status + " != PASS_BASE");
            testExitCode = FAILED;
        } else {
            log2("debuggee returned expected exit status: " +
                    status + " == PASS_BASE");
        }

        if (testExitCode != PASSED) {
            System.out.println("TEST FAILED");
        }
        return testExitCode;
    }
}
