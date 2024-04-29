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

package nsk.jdwp.Method.LineTable;

import java.io.*;

import nsk.share.*;
import nsk.share.jpda.*;
import nsk.share.jdwp.*;

/**
 * Test for JDWP command: Method.LineTable.
 *
 * See linetable001.README for description of test execution.
 *
 * This class represents debugger part of the test.
 * Test is executed by invoking method runIt().
 * JDWP command is tested in the method testCommand().
 *
 * @see #runIt()
 * @see #testCommand()
 */
public class linetable001 {

    static final int JCK_STATUS_BASE = 95;
    static final int PASSED = 0;
    static final int FAILED = 2;

    static final String READY = "ready";
    static final String QUIT = "quit";

    static final String PACKAGE_NAME = "nsk.jdwp.Method.LineTable";
    static final String TEST_CLASS_NAME = PACKAGE_NAME + "." + "linetable001";
    static final String DEBUGEE_CLASS_NAME = TEST_CLASS_NAME + "a";

    static final String JDWP_COMMAND_NAME = "Method.LineTable";
    static final int JDWP_COMMAND_ID = JDWP.Command.Method.LineTable;

    static final String TESTED_CLASS_NAME = DEBUGEE_CLASS_NAME + "$" + "TestedClass";
    static final String TESTED_CLASS_SIGNATURE = "L" + TESTED_CLASS_NAME.replace('.', '/') + ";";

    static final String TESTED_METHOD_NAME = "testedMethod";

    static final int FIRST_LINE_NUMBER = linetable001a.FIRST_LINE_NUMBER;
    static final int LAST_LINE_NUMBER = linetable001a.LAST_LINE_NUMBER;

    ArgumentHandler argumentHandler = null;
    Log log = null;
    Binder binder = null;
    Debugee debugee = null;
    Transport transport = null;
    IOPipe pipe = null;

    boolean success = true;


    /**
     * Start test from command line.
     */
    public static void main (String argv[]) {
        System.exit(run(argv,System.out) + JCK_STATUS_BASE);
    }

    /**
     * Start JCK-compilant test.
     */
    public static int run(String argv[], PrintStream out) {
        return new linetable001().runIt(argv, out);
    }


    /**
     * Perform test execution.
     */
    public int runIt(String argv[], PrintStream out) {

        argumentHandler = new ArgumentHandler(argv);
        log = new Log(out, argumentHandler);

        try {
            log.display("\n>>> Preparing debugee for testing \n");

            binder = new Binder(argumentHandler, log);
            log.display("Launching debugee");
            debugee = binder.bindToDebugee(DEBUGEE_CLASS_NAME);
            transport = debugee.getTransport();
            pipe = debugee.createIOPipe();

            prepareDebugee();

            try {
                log.display("\n>>> Obtaining requred data from debugee \n");

                log.display("Getting classID by signature:\n"
                            + "  " + TESTED_CLASS_SIGNATURE);
                long classID = debugee.getReferenceTypeID(TESTED_CLASS_SIGNATURE);
                log.display("  got classID: " + classID);

                log.display("Getting methodID by name: " + TESTED_METHOD_NAME);
                long methodID = debugee.getMethodID(classID, TESTED_METHOD_NAME, true);
                log.display("  got methodID: " + methodID);

                log.display("\n>>> Testing JDWP command \n");
                testCommand(classID, methodID);

            } finally {
                log.display("\n>>> Finishing test \n");
                quitDebugee();
            }

        } catch (Failure e) {
            log.complain("TEST FAILED: " + e.getMessage());
            success = false;
        } catch (Exception e) {
            e.printStackTrace(out);
            log.complain("Caught unexpected exception while running the test:\n\t" + e);
            success = false;
        }

        if (!success) {
            log.complain("TEST FAILED");
            return FAILED;
        }

        out.println("TEST PASSED");
        return PASSED;

    }

    /**
     * Prepare debugee for testing and waiting for ready signal.
     */
    void prepareDebugee() {
        log.display("Waiting for VM_INIT event");
        debugee.waitForVMInit();

        log.display("Querying for IDSizes");
        debugee.queryForIDSizes();

        log.display("Resuming debugee VM");
        debugee.resume();

        log.display("Waiting for signal from debugee: " + READY);
        String signal = pipe.readln();
        log.display("Received signal from debugee: " + signal);
        if (! signal.equals(READY)) {
            throw new TestBug("Unexpected signal received from debugee: " + signal
                            + " (expected: " + READY + ")");
        }
    }

    /**
     * Sending debugee signal to quit and waiting for it exits.
     */
    void quitDebugee() {
        log.display("Sending signal to debugee: " + QUIT);
        pipe.println(QUIT);

        log.display("Waiting for debugee exits");
        int code = debugee.waitFor();

        if (code == JCK_STATUS_BASE + PASSED) {
            log.display("Debugee PASSED with exit code: " + code);
        } else {
            log.complain("Debugee FAILED with exit code: " + code);
            success = false;
        }
    }

    /**
     * Perform testing JDWP command for specified TypeID.
     */
    void testCommand(long classID, long methodID) {
        log.display("Create command packet:");
        log.display("Command: " + JDWP_COMMAND_NAME);
        CommandPacket command = new CommandPacket(JDWP_COMMAND_ID);
        log.display("  referenceTypeID: " + classID);
        command.addReferenceTypeID(classID);
        log.display("  methodID: " + methodID);
        command.addMethodID(methodID);
        command.setLength();

        try {
            log.display("Sending command packet:\n" + command);
            transport.write(command);
        } catch (IOException e) {
            log.complain("Unable to send command packet:\n\t" + e);
            success = false;
            return;
        }

        ReplyPacket reply = new ReplyPacket();

        try {
            log.display("Waiting for reply packet");
            transport.read(reply);
            log.display("Reply packet received:\n" + reply);
        } catch (IOException e) {
            log.complain("Unable to read reply packet:\n\t" + e);
            success = false;
            return;
        }

        try{
            log.display("Checking reply packet header");
            reply.checkHeader(command.getPacketID());
        } catch (BoundException e) {
            log.complain("Bad header of reply packet:\n\t" + e.getMessage());
            success = false;
        }

        log.display("Parsing reply packet:");
        reply.resetPosition();


        long start = 0;
        try {
            start = reply.getLong();
            log.display("  start: " + start);
        } catch (BoundException e) {
            log.complain("Unable to extract start line index from reply packet:\n\t"
                        + e.getMessage());
            success = false;
            return;
        }

        if (start < 0) {
            log.complain("Negative value of start code index in reply packet: " + start);
            success = false;
        }

        long end = 0;
        try {
            end = reply.getLong();
            log.display("  end: " + end);
        } catch (BoundException e) {
            log.complain("Unable to extract end line index from reply packet:\n\t"
                        + e.getMessage());
            success = false;
            return;
        }

        if (start < 0) {
            log.complain("Negative value of end code index in reply packet: " + end);
            success = false;
        }

        if (start > end) {
            log.complain("Start code index (" + start
                        + ") is greater than end code index (" + end + ")");
            success = false;
        } else if (start == end) {
            log.complain("Start code index (" + start
                        + ") is equal to end code index (" + end + ")");
            success = false;
        }

        int lines = 0;
        try {
            lines = reply.getInt();
            log.display("  lines: " + lines);
        } catch (BoundException e) {
            log.complain("Unable to extract number of lines from reply packet:\n\t"
                        + e.getMessage());
            success = false;
            return;
        }

        if (lines < 0) {
            log.complain("Negative number of lines in reply packet: " + lines);
            success = false;
            return;
        }

        if (lines == 0) {
            log.complain("Zero number of lines in reply packet: " + lines);
            success = false;
            return;
        }

        long lineCodeIndex = 0, prevLineCodeIndex = 0;
        int lineNumber = 0, prevLineNumber = 0;

        for (int i = 0; i < lines; i++) {
            log.display("  line #" + i + ":");

            try {
                lineCodeIndex = reply.getLong();
                log.display("    lineCodeIndex: " + lineCodeIndex);
            } catch (BoundException e) {
                log.complain("Unable to extract code index of line #" + i
                            + " from reply packet:\n\t"
                            + e.getMessage());
                success = false;
                return;
            }

            if (lineCodeIndex < start) {
                log.complain("Code index of line #" + i + " (" + lineCodeIndex
                            + ") is less than start code index (" + start + ")");
                success = false;
            }

            if (lineCodeIndex > end) {
                log.complain("Code index of line #" + i + " (" + lineCodeIndex
                            + ") is greater than end code index (" + end + ")");
                success = false;
            }

            if (i == 0) {
                if (lineCodeIndex != start) {
                    log.complain("Code index of first line (" + lineCodeIndex
                                + ") is not equal to start code index (" + start + ")");
                    success = false;
                }
            }

            if (i > 0) {
                if (lineCodeIndex < prevLineCodeIndex) {
                    log.complain("Code index of line #" + i + " (" + lineCodeIndex
                                + ") is less than code index of previous line ("
                                + prevLineCodeIndex + ")");
                    success = false;
                } else  if (lineCodeIndex == prevLineCodeIndex) {
                    log.complain("Code index of line #" + i + " (" + lineCodeIndex
                                + ") is equal to code index of previous line ("
                                + prevLineCodeIndex + ")");
                    success = false;
                }
            }

            try {
                lineNumber = reply.getInt();
                log.display("    lineNumber: " + lineNumber);
            } catch (BoundException e) {
                log.complain("Unable to extract number of line #" + i
                            + " from reply packet:\n\t"
                            + e.getMessage());
                success = false;
                return;
            }

            if (lineNumber < 0) {
                log.complain("Number of line #" + i + " (" + lineNumber
                            + ") is negative");
                success = false;
            }

            if (lineNumber < FIRST_LINE_NUMBER) {
                log.complain("Number of line #" + i + " (" + lineNumber
                            + ") is less than expected (" + FIRST_LINE_NUMBER + ")");
                success = false;
            }

            if (lineNumber > LAST_LINE_NUMBER) {
                log.complain("Number of line #" + i + " (" + lineNumber
                            + ") is greater than expected (" + LAST_LINE_NUMBER + ")");
                success = false;
            }

            if (i > 0) {
                if (lineNumber < prevLineNumber) {
                    log.complain("Number of line #" + i + " (" + lineCodeIndex
                                + ") is less than number of previous line ("
                                + prevLineNumber + ")");
                    success = false;
                } else  if (lineNumber == prevLineNumber) {
                    log.complain("Number of line #" + i + " (" + lineCodeIndex
                                + ") is equal to number of previous line ("
                                + prevLineNumber + ")");
                    success = false;
                } else if (lineNumber != prevLineNumber + 1) {
                    log.complain("Number of line #" + i + " (" + lineCodeIndex
                                + ") does not follows to number of previous line ("
                                + prevLineNumber + ")");
                    success = false;
                }
            }

            prevLineCodeIndex = lineCodeIndex;
            prevLineNumber = lineNumber;
        }

        if (!reply.isParsed()) {
            log.complain("Extra trailing bytes found in reply packet at: "
                        + reply.offsetString());
            success = false;
        }
    }

}
