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

package nsk.jdwp.ArrayReference.GetValues;

import java.io.*;

import nsk.share.*;
import nsk.share.jpda.*;
import nsk.share.jdwp.*;

/**
 * Test for JDWP command: ArrayReference.GetValues.
 *
 * See getvalues001.README for description of test execution.
 *
 * Test is executed by invoking method runIt().
 * JDWP command is tested in method testCommand().
 *
 * @see #runIt()
 * @see #testCommand()
 */
public class getvalues001 {

    static final int JCK_STATUS_BASE = 95;
    static final int PASSED = 0;
    static final int FAILED = 2;

    static final String READY = "ready";
    static final String QUIT = "quit";

    static final String PACKAGE_NAME = "nsk.jdwp.ArrayReference.GetValues";
    static final String TEST_CLASS_NAME = PACKAGE_NAME + "." + "getvalues001";
    static final String DEBUGEE_CLASS_NAME = TEST_CLASS_NAME + "a";

    static final String JDWP_COMMAND_NAME = "ArrayReference.GetValues";
    static final int JDWP_COMMAND_ID = JDWP.Command.ArrayReference.GetValues;

    static final String TESTED_CLASS_NAME = DEBUGEE_CLASS_NAME + "$" + "TestedClass";
    static final String TESTED_CLASS_SIGNATURE = "L" + TESTED_CLASS_NAME.replace('.', '/') + ";";

    static final String ARRAY_FIELD_NAME = getvalues001a.ARRAY_FIELD_NAME;
    static final int ARRAY_LENGTH = getvalues001a.ARRAY_LENGTH;

    static final int ARRAY_FIRST_INDEX = 4;
    static final int ARRAY_ITEMS_COUNT = 10;

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
        return new getvalues001().runIt(argv, out);
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

                log.display("Getting ReferenceTypeID by signature:\n"
                            + "  " + TESTED_CLASS_SIGNATURE);
                long classID = debugee.getReferenceTypeID(TESTED_CLASS_SIGNATURE);
                log.display("  got classID: " + classID);

                log.display("Getting arrayID value from static field: "
                            + ARRAY_FIELD_NAME);
                long arrayID = queryObjectID(classID,
                            ARRAY_FIELD_NAME, JDWP.Tag.ARRAY);
                log.display("  got arrayID: " + arrayID);

                log.display("\n>>> Testing JDWP command \n");
                testCommand(arrayID);

            } finally {
                log.display("\n>>> Finishing test \n");
                quitDebugee();
            }

        } catch (Failure e) {
            log.complain("TEST FAILED: " + e.getMessage());
            e.printStackTrace(out);
            success = false;
        } catch (Exception e) {
            log.complain("Caught unexpected exception:\n" + e);
            e.printStackTrace(out);
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
            throw new TestBug("Unexpected signal received form debugee: " + signal
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
     * Query debuggee for objectID value of static class field.
     */
    long queryObjectID(long classID, String fieldName, byte tag) {
        long fieldID = debugee.getClassFieldID(classID, fieldName, true);
        JDWP.Value value = debugee.getStaticFieldValue(classID, fieldID);

        if (value.getTag() != tag) {
            throw new Failure("Wrong objectID tag received from field \"" + fieldName
                            + "\": " + value.getTag() + " (expected: " + tag + ")");
        }

        long objectID = ((Long)value.getValue()).longValue();
        return objectID;
    }

    /**
     * Perform testing JDWP command for specified objectID.
     */
    void testCommand(long arrayID) {
        log.display("Create command packet:");
        log.display("Command: " + JDWP_COMMAND_NAME);
        CommandPacket command = new CommandPacket(JDWP_COMMAND_ID);

        log.display("  arrayID: " + arrayID);
        command.addObjectID(arrayID);
        log.display("  firstIndex: " + ARRAY_FIRST_INDEX);
        command.addInt(ARRAY_FIRST_INDEX);
        log.display("  length: " + ARRAY_ITEMS_COUNT);
        command.addInt(ARRAY_ITEMS_COUNT);
        command.setLength();

        try {
            log.display("Sending command packet:\n" + command);
            transport.write(command);
        } catch (IOException e) {
            log.complain("Unable to send command packet:\n" + e);
            success = false;
            return;
        }

        ReplyPacket reply = new ReplyPacket();

        try {
            log.display("Waiting for reply packet");
            transport.read(reply);
            log.display("Reply packet received:\n" + reply);
        } catch (IOException e) {
            log.complain("Unable to read reply packet:\n" + e);
            success = false;
            return;
        }

        try{
            log.display("Checking reply packet header");
            reply.checkHeader(command.getPacketID());
        } catch (BoundException e) {
            log.complain("Bad header of reply packet: " + e.getMessage());
            success = false;
        }

        log.display("Parsing reply packet:");
        reply.resetPosition();

        byte tag = (byte)0;
        try {
            tag = reply.getByte();
            log.display("  tag: " + tag);

        } catch (BoundException e) {
            log.complain("Unable to extract values tag from reply packet:\n\t"
                        + e.getMessage());
            success = false;
        }

        if (tag != JDWP.Tag.INT) {
            log.complain("Unexpected values tag received:" + tag
                        + " (expected: " + JDWP.Tag.INT + ")");
            success = false;
        }

        int values = 0;
        try {
            values = reply.getInt();
            log.display("  values: " + values);

        } catch (BoundException e) {
            log.complain("Unable to extract number of values from reply packet:\n\t"
                        + e.getMessage());
            success = false;
        }

        if (values < 0) {
            log.complain("Negative number of values received:" + values
                        + " (expected: " + ARRAY_ITEMS_COUNT + ")");
            success = false;
        } else if (values != ARRAY_ITEMS_COUNT) {
            log.complain("Unexpected number of values received:" + values
                        + " (expected: " + ARRAY_ITEMS_COUNT + ")");
            success = false;
        }

        for (int i = 0; i < values; i++ ) {
            int index = i + ARRAY_FIRST_INDEX;
            log.display("  value #" + i + " (index: " + index  + ")");

            JDWP.UntaggedValue value = null;
            try {
                value = reply.getUntaggedValue(JDWP.Tag.INT);
                log.display("    untagged_value: " + value);
            } catch (BoundException e) {
                log.complain("Unable to extract " + i + " value from reply packet:\n\t"
                            + e.getMessage());
                success = false;
                break;
            }

            int intValue = ((Integer)value.getValue()).intValue();
            if (intValue != index * 10) {
                log.complain("Unexpected value for " + index + " component received: "
                            + intValue + " (expected: " + (index * 10) + ")");
                success = false;
            }
        }

        if (! reply.isParsed()) {
            log.complain("Extra trailing bytes found in reply packet at: "
                        + "0x" + reply.toHexString(reply.currentDataPosition(), 4));
            success = false;
        }
    }

}
