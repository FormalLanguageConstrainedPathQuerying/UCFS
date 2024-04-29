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

package nsk.jdi.Location.declaringType;

import nsk.share.*;
import nsk.share.jpda.*;
import nsk.share.jdi.*;

import com.sun.jdi.*;
import java.util.*;
import java.io.*;

/**
 * The test for the implementation of an object of the type     <BR>
 * Location.                                                    <BR>
 *                                                              <BR>
 * The test checks up that results of the method                <BR>
 * <code>com.sun.jdi.Location.declaringType()</code>            <BR>
 * complie with its specification.                              <BR>
 * <BR>
 * The case for testing includes two types in a debuggee,               <BR>
 * an Interface type and a Class type implementing it.                  <BR>
 * The Class contains a method, the Interface static initialize.        <BR>
 * A debugger gets two ReferenceType objects,                           <BR>
 * a testedclass and a testediface mirroring the types,                 <BR>
 * and for each tested type, performs the following:                    <BR>
 * - gets a List returned by the methods testedclass.allLineLocations();<BR>
 * - perform a loop to check up that for each Location in the List,     <BR>
 * call to the method Location.declaringType() returns ReferenceType    <BR>
 * object equal to one got at the beginning of the test.                <BR>
 */

public class declaringtype001 {

    static final int PASSED = 0;
    static final int FAILED = 2;
    static final int PASS_BASE = 95;

    static final String
    sHeader1 = "\n==> nsk/jdi/Location/declaringType/declaringtype001  ",
    sHeader2 = "--> debugger: ",
    sHeader3 = "##> debugger: ";


    public static void main (String argv[]) {
        int result = run(argv, System.out);
        if (result != 0) {
            throw new RuntimeException("TEST FAILED with result " + result);
        }
    }

    public static int run (String argv[], PrintStream out) {
        return new declaringtype001().runThis(argv, out);
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
        "nsk.jdi.Location.declaringType.declaringtype001a";

    String mName = "nsk.jdi.Location.declaringType";


    static ArgumentHandler      argsHandler;

    static int waitTime;

    static VirtualMachine vm = null;

    static int  testExitCode = PASSED;

    static final int returnCode0 = 0;
    static final int returnCode1 = 1;
    static final int returnCode2 = 2;
    static final int returnCode3 = 3;
    static final int returnCode4 = 4;


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

        waitTime = argsHandler.getWaitTime();


        IOPipe pipe     = new IOPipe(debuggee);

        debuggee.redirectStderr(out);
        log2(debuggeeName + " debuggee launched");
        debuggee.resume();

        String line = pipe.readln();
        if ((line == null) || !line.equals("ready")) {
            log3("signal received is not 'ready' but: " + line);
            return FAILED;
        } else {
            log2("'ready' recieved");
        }

        vm = debuggee.VM();

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

            log1("new checkready: #" + i);


            log2("......testing Class ReferenceType");

            String testedclassName = mName + ".TestClass";

            log2("       getting: List classes = vm.classesByName(testedclassName); expected size == 1");
            List classes = vm.classesByName(testedclassName);
            int size = classes.size();
            if (size != 1) {
                log3("ERROR: classes.size() != 1 : " + size);
                testExitCode = FAILED;
                break ;
            }

            log2("      ReferenceType testedclass = (ReferenceType) classes.get(0)");
            ReferenceType testedclass = (ReferenceType) classes.get(0);

            log2("      getting: TestClass.allLineLocations(); no AbsentInformationException expected");
            List lineLocations = null;
            try {
                lineLocations = testedclass.allLineLocations();
            } catch ( AbsentInformationException e) {
                log3("ERROR: AbsentInformationException");
                testExitCode = FAILED;
                break;
            }
            size = lineLocations.size();
            if (size == 0) {
                log3("ERROR: lineLocations.size() == 0");
                testExitCode = FAILED;
                break;
            }

            ListIterator li = lineLocations.listIterator();

            log2("......checking equality: location.declareingType().equals(testedclass)");
            for (int ifor = 0; li.hasNext(); ifor++) {
                Location loc = (Location) li.next();

                if (!loc.declaringType().equals(testedclass)) {
                    log3("ERROR: !loc.declareingType().equals(testedclass); index in List : " + ifor);
                    testExitCode = FAILED;
                }
            }


            log2("......testing Interface ReferenceType");

            String testedifaceName = mName + ".TestIface";

            log2("       getting: List ifaces = vm.classesByName(testedifaceName); expected size == 1");
            List ifaces = vm.classesByName(testedifaceName);
            size = ifaces.size();
            if (size != 1) {
                log3("ERROR: ifaces.size() != 1 : " + size);
                testExitCode = FAILED;
                break ;
            }

            log2("      ReferenceType testediface = (ReferenceType) classes.get(0)");
            ReferenceType testediface = (ReferenceType) ifaces.get(0);

            log2("      getting: TestIface.allLineLocations(); no AbsentInformationException expected");
            try {
                lineLocations = testediface.allLineLocations();
            } catch ( AbsentInformationException e) {
                log3("ERROR: AbsentInformationException");
                testExitCode = FAILED;
                break;
            }
            size = lineLocations.size();
            if (size == 0) {
                log3("ERROR: lineLocations.size() == 0");
                testExitCode = FAILED;
                break;
            }

            li = lineLocations.listIterator();

            log2("......checking equality: location.declareingType().equals(testediface)");
            for (int ifor = 0; li.hasNext(); ifor++) {
                Location loc = (Location) li.next();

                if (!loc.declaringType().equals(testediface)) {
                    log3("ERROR: !loc.declareingType().equals(testediface); index in List : " + ifor);
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
            logHandler.complain("TEST FAILED");
        }
        return testExitCode;
    }
}
