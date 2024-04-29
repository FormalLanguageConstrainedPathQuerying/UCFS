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

package nsk.jdi.ExceptionEvent.catchLocation;

import com.sun.jdi.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.*;

import java.io.*;
import java.util.Iterator;
import java.util.List;

import nsk.share.*;
import nsk.share.jpda.*;
import nsk.share.jdi.*;



public class location001 {
    static final int PASSED = 0;
    static final int FAILED = 2;
    static final int JCK_STATUS_BASE = 95;

    static final int TIMEOUT_DELTA = 1000; 

    static final String COMMAND_READY = "ready";
    static final String COMMAND_QUIT  = "quit";
    static final String COMMAND_GO    = "go";
    static final String COMMAND_DONE  = "done";
    static final String COMMAND_ERROR = "error";

    static final String DEBUGGEE_NAME = "nsk.jdi.ExceptionEvent.catchLocation.location001a";

    static final String USER_EXCEPTION = DEBUGGEE_NAME + "Exception";
    static final String USER_ERROR     = DEBUGGEE_NAME + "Error";
    static final String USER_THROWABLE = DEBUGGEE_NAME + "Throwable";
    static final String JAVA_EXCEPTION = "java.lang.NumberFormatException";
    static final String JAVA_ERROR     = "java.lang.StackOverflowError";

    static private Debugee debuggee;
    static private VirtualMachine vm;
    static private IOPipe pipe;
    static private Log log;
    static private ArgumentHandler argHandler;
    static private EventSet eventSet;

    static private long eventTimeout;

    static private ExceptionRequest  checkedRequest;
    static private ReferenceType     checkedClass;
    static private ThreadReference   checkedThread;

    static private ReferenceType userException;
    static private ReferenceType userError;
    static private ReferenceType userThrowable;


    static private boolean userExceptionReceived;
    static private boolean userErrorReceived;
    static private boolean userThrowableReceived;
    static private boolean javaExceptionReceived;
    static private boolean javaErrorReceived;

    static private boolean eventsReceived;
    static private boolean testFailed;

    static private volatile boolean exceptionsThrown;

    public static void main (String argv[]) {
         int result = run(argv,System.out);
         if (result != 0) {
             throw new RuntimeException("TEST FAILED with result " + result);
         }
    }

    public static int run(final String args[], final PrintStream out) {

        testFailed = false;
        userExceptionReceived = false;
        userErrorReceived = false;
        userThrowableReceived = false;
        javaExceptionReceived = false;
        javaErrorReceived = false;
        eventsReceived = false;

        argHandler = new ArgumentHandler(args);
        log = new Log(out, argHandler);
        eventTimeout = argHandler.getWaitTime() * 60 * 1000; 

        Binder binder = new Binder(argHandler, log);
        log.display("Connecting to debuggee");
        debuggee = binder.bindToDebugee(DEBUGGEE_NAME);
        debuggee.redirectStderr(log, "location001a >");

        pipe = debuggee.createIOPipe();
        vm = debuggee.VM();
        EventRequestManager erManager = vm.eventRequestManager();

        log.display("Resuming debuggee");
        debuggee.resume();

        try {

            log.display("Waiting for command: " + COMMAND_READY);
            String command = pipe.readln();
            if (!command.equals(COMMAND_READY)) {
                throw new Failure("TEST BUG: unexpected debuggee's command: " + command);
            }

            log.display("Getting loaded classes in debuggee");
            if ((checkedClass = debuggee.classByName(DEBUGGEE_NAME)) == null) {
                throw new Failure("TEST BUG: cannot find " + DEBUGGEE_NAME);
            }

            if ((userException = debuggee.classByName(USER_EXCEPTION)) == null) {
                throw new Failure("TEST BUG: cannot find " + USER_EXCEPTION);
            }

            if ((userError = debuggee.classByName(USER_ERROR)) == null) {
                throw new Failure("TEST BUG: cannot find " + USER_ERROR);
            }

            if ((userThrowable = debuggee.classByName(USER_THROWABLE)) == null) {
                throw new Failure("TEST BUG: cannot find " + USER_THROWABLE);
            }

            log.display("Getting reference to main thread");
            Iterator threadIterator = vm.allThreads().iterator();
            while (threadIterator.hasNext()) {
                ThreadReference curThread = (ThreadReference) threadIterator.next();
                if (curThread.name().equals("main")) {
                     checkedThread = curThread;
                }
            }
            if (checkedThread == null) {
                throw new Failure("TEST BUG: unable to find reference to main thread");
            }

            log.display("Creating ExceptionRequest");
            boolean notifyCaught = true;
            boolean notifyUncaught = true;
            if ((checkedRequest = erManager.createExceptionRequest(null, notifyCaught, notifyUncaught)) == null) {
                throw new Failure("TEST BUG: unable to create ExceptionRequest");
            }

            class EventHandler extends Thread {
                public void run() {
                    while (!(exceptionsThrown && eventsReceived)) {
                        eventSet = null;
                        try {
                            eventSet = vm.eventQueue().remove(TIMEOUT_DELTA);
                        } catch (InterruptedException e) {
                            log.complain("Unexpected InterruptedException while receiving event: " + e);
                            break;
                        }

                        if (eventSet == null) {
                            continue;
                        }

                        EventIterator eventIterator = eventSet.eventIterator();
                        while (eventIterator.hasNext()) {

                            Event event = eventIterator.nextEvent();
                            log.display("\nEvent received:\n  " + event);

                            if (EventFilters.filtered(event))
                                continue;

                            if (event instanceof ExceptionEvent) {

                                ExceptionEvent castedEvent   = (ExceptionEvent)event;
                                ReferenceType  eventRefType  = castedEvent.exception().referenceType();
                                Location  eventLocation      = castedEvent.location();
                                Location  eventCatchLocation = castedEvent.catchLocation();

                                if (eventRefType.equals(userException)) {
                                    log.display("ExceptionEvent for " + USER_EXCEPTION + " is received");
                                    userExceptionReceived = true;
                                    if (eventLocation.lineNumber() != location001a.userExceptionLocation) {
                                        log.complain("FAILURE 1: incorrect location for " + USER_EXCEPTION +
                                         "\nexpected: " + location001a.userExceptionLocation + ", got: " + eventLocation.lineNumber());
                                        testFailed = true;
                                    }
                                    if (eventCatchLocation.lineNumber() != location001a.userExceptionCatchLocation) {
                                        log.complain("FAILURE 1: incorrect catchLocation for " + USER_EXCEPTION +
                                         "\nexpected: " + location001a.userExceptionCatchLocation + ", got: " + eventCatchLocation.lineNumber());
                                        testFailed = true;
                                    }
                                    if (!(eventLocation.declaringType().equals(checkedClass))) {
                                        log.complain("FAILURE 2: catchLocation.declaringType() does not equal to debuggee class:");
                                        testFailed = true;
                                        log.complain(eventLocation.declaringType().name());
                                    }

                                } else if (eventRefType.equals(userError)) {
                                    log.display("ExceptionEvent for " + USER_ERROR + " is received");
                                    userErrorReceived = true;
                                    if (eventLocation.lineNumber() != location001a.userErrorLocation) {
                                        log.complain("FAILURE 3: incorrect location for " + USER_ERROR +
                                         "\nexpected: " + location001a.userErrorLocation + ", got: " + eventLocation.lineNumber());
                                        testFailed = true;
                                    }
                                    if (eventCatchLocation.lineNumber() != location001a.userErrorCatchLocation) {
                                        log.complain("FAILURE 3: incorrect catchLocation for " + USER_ERROR +
                                         "\nexpected: " + location001a.userErrorCatchLocation + ", got: " + eventCatchLocation.lineNumber());
                                        testFailed = true;
                                    }
                                    if (!(eventLocation.declaringType().equals(checkedClass))) {
                                        log.complain("FAILURE 4: catchLocation.declaringType() does not equal to debuggee class:");
                                        testFailed = true;
                                        log.complain(eventLocation.declaringType().name());
                                    }

                                } else if (eventRefType.equals(userThrowable)) {
                                    log.display("ExceptionEvent for " + USER_THROWABLE + " is received");
                                    userThrowableReceived = true;
                                    if (eventLocation.lineNumber() != location001a.userThrowableLocation) {
                                        log.complain("FAILURE 5: incorrect location for " + USER_THROWABLE +
                                         "\nexpected: " + location001a.userThrowableLocation + ", got: " + eventLocation.lineNumber());
                                        testFailed = true;
                                    }
                                    if (eventCatchLocation.lineNumber() != location001a.userThrowableCatchLocation) {
                                        log.complain("FAILURE 5: incorrect catchLocation for " + USER_THROWABLE +
                                         "\nexpected: " + location001a.userThrowableCatchLocation + ", got: " + eventCatchLocation.lineNumber());
                                        testFailed = true;
                                    }
                                    if (!(eventLocation.declaringType().equals(checkedClass))) {
                                        log.complain("FAILURE 6: catchLocation.declaringType() does not equal to debuggee class:");
                                        testFailed = true;
                                        log.complain(eventLocation.declaringType().name());
                                    }
                                } else {
                                    String eventTypeName = eventRefType.name();
                                    if (eventTypeName.equals(JAVA_EXCEPTION)) {
                                        log.display("ExceptionEvent for " + JAVA_EXCEPTION + " is received");
                                        javaExceptionReceived = true;
                                        if (eventCatchLocation.lineNumber() != location001a.javaExceptionCatchLocation) {
                                            log.complain("FAILURE 7: incorrect catchLocation for " + JAVA_EXCEPTION +
                                             "\nexpected: " + location001a.javaExceptionCatchLocation + ", got: " + eventCatchLocation.lineNumber());
                                            testFailed = true;
                                        }

                                    } else if (eventTypeName.equals(JAVA_ERROR)) {
                                        log.display("ExceptionEvent for " + JAVA_ERROR + " is received");
                                        javaErrorReceived = true;
                                        if (eventCatchLocation.lineNumber() != location001a.javaErrorCatchLocation) {
                                            log.complain("FAILURE 8: incorrect catchLocation for " + JAVA_ERROR +
                                             "\nexpected: " + location001a.javaErrorCatchLocation + ", got: " + eventCatchLocation.lineNumber());
                                            testFailed = true;
                                        }
                                    }
                                }
                            }


                        } 

                        log.display("Resuming event set");
                        eventSet.resume();

                        eventsReceived = userExceptionReceived && userErrorReceived
                                        && userThrowableReceived && javaExceptionReceived
                                        && javaErrorReceived;

                    } 

                    log.display("eventHandler completed");

                } 

            } 

            EventHandler eventHandler = new EventHandler();
            log.display("Starting eventHandler");
            eventHandler.start();

            log.display("Enabling ExceptionRequest");
            checkedRequest.enable();

            log.display("Sending command: " + COMMAND_GO);
            pipe.println(COMMAND_GO);

            log.display("");

            log.display("Waiting for command: " + COMMAND_DONE);
            command = pipe.readln();
            if (command.equals(COMMAND_ERROR)) {
                throw new Failure("TEST BUG: Unable to thrown all exceptions in debuggee");
            }
            if (!command.equals(COMMAND_DONE)) {
                throw new Failure("TEST BUG: unknown debuggee's command: " + command);
            }

            exceptionsThrown = true;

            log.display("");

            log.display("Waiting for all expected events received");
            try {
                eventHandler.join(eventTimeout);
                if (eventHandler.isAlive()) {
                    log.complain("FAILURE 20: Timeout for waiting event was exceeded");
                    eventHandler.interrupt();
                    testFailed = true;
                }
            } catch (InterruptedException e) {
                log.complain("TEST INCOMPLETE: InterruptedException caught while waiting for eventHandler's death");
                testFailed = true;
            }

            if (!userExceptionReceived) {
                log.complain("FAILURE 9: " + USER_EXCEPTION + " was not received received");
                testFailed= true;
            }
            if (!userErrorReceived) {
                log.complain("FAILURE 10: " + USER_ERROR + " was not received received");
                testFailed= true;
            }
            if (!userThrowableReceived) {
                log.complain("FAILURE 11: " + USER_THROWABLE + " was not received received");
                testFailed= true;
            }
            if (!javaExceptionReceived) {
                log.complain("FAILURE 12: " + JAVA_EXCEPTION + " was not received received");
                testFailed= true;
            }
            if (!javaErrorReceived) {
                log.complain("FAILURE 13: " + JAVA_ERROR + " was not received received");
                testFailed= true;
            }


        } catch (Failure e) {
            log.complain("TEST FAILURE: " + e.getMessage());
            testFailed = true;
        } catch (Exception e) {
            log.complain("Unexpected exception: " + e);
            e.printStackTrace(out);
            testFailed = true;
        } finally {

            log.display("");

            if (checkedRequest != null && checkedRequest.isEnabled()) {
                log.display("Disabling event request");
                checkedRequest.disable();
            }

            log.display("Sending command: " + COMMAND_QUIT);
            pipe.println(COMMAND_QUIT);

            log.display("Waiting for debuggee terminating");
            int debuggeeStatus = debuggee.endDebugee();
            if (debuggeeStatus == PASSED + JCK_STATUS_BASE) {
                log.display("Debuggee PASSED with exit code: " + debuggeeStatus);
            } else {
                log.complain("Debuggee FAILED with exit code: " + debuggeeStatus);
                testFailed = true;
            }
        }

        if (testFailed) {
            log.complain("TEST FAILED");
            return FAILED;
        }

        log.display("TEST PASSED");
        return PASSED;
    }
}
