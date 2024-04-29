/*
 * Copyright (c) 2007, 2024, Oracle and/or its affiliates. All rights reserved.
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

package nsk.jdi.AttachingConnector.attachnosuspend;

import com.sun.jdi.Bootstrap;
import com.sun.jdi.connect.*;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.ReferenceType;

import java.io.*;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import jdk.test.lib.JDWP;
import nsk.share.*;
import nsk.share.jpda.*;
import nsk.share.jdi.*;


/**
 * The test checks that debugger may establish connection with
 * a target VM via <code>com.sun.jdi.SocketAttach</code> connector.
 */
public class attachnosuspend001 {
    static final int PASSED = 0;
    static final int FAILED = 2;
    static final int JCK_STATUS_BASE = 95;
    static final String DEBUGEE_CLASS =
        "nsk.jdi.AttachingConnector.attachnosuspend.attachnosuspend001t";

    private Log log;

    private VirtualMachine vm;

    private int attempts;             
    private int delay = 4000;         

    IORedirector outRedirector;
    IORedirector errRedirector;

    public static void main (String argv[]) {
        int result = run(argv,System.out);
        if (result != 0) {
            throw new RuntimeException("TEST FAILED with result " + result);
        }
    }

    public static int run(String argv[], PrintStream out) {
        return new attachnosuspend001().runIt(argv, out);
    }

    private int runIt(String argv[], PrintStream out) {
        ArgumentHandler argHandler = new ArgumentHandler(argv);

        if (argHandler.shouldPass("com.sun.jdi.SocketAttach"))
            return PASSED;

        log = new Log(out, argHandler);

        String args[] = argHandler.getArguments();

        try {
            delay = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            log.complain("Incorrect test parameter: timeout value must be an integer");
            return FAILED;
        } catch (ArrayIndexOutOfBoundsException e) {
        }
        long timeout = argHandler.getWaitTime() * 60 * 1000;
        attempts = (int)(timeout / delay);

        String java = argHandler.getLaunchExecPath()
                        + " " + argHandler.getLaunchOptions();
        String cmd = java
                + " -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=0"
                + " " + DEBUGEE_CLASS;

        Binder binder = new Binder(argHandler, log);
        log.display("command: " + cmd);
        Debugee debugee = binder.startLocalDebugee(cmd);
        JDWP.ListenAddress listenAddress = debugee.redirectOutputAndDetectListeningAddress(log);
        String port = listenAddress.address();

        if ((vm = attachTarget(argHandler.getTestHost(), port)) == null) {
            log.complain("TEST: Unable to attach the debugee VM");
            debugee.close();
            return FAILED;
        }

        log.display("debugee VM: name=" + vm.name() + " JRE version=" +
            vm.version() + "\n\tdescription=" + vm.description());

        debugee.setupVM(vm);


        log.display("\nwaiting for debugee VM exit");
        int code = debugee.waitFor();
        if (code != (JCK_STATUS_BASE+PASSED)) {
            log.complain("Debugee VM has crashed: exit code=" +
                code);
            return FAILED;
        }
        log.display("debugee VM: exit code=" + code);
        return PASSED;
    }

    private VirtualMachine attachTarget(String host, String port) {
        Connector.Argument arg;

        if (port == null) {
            log.complain("TEST: port number is required!");
            return null;
        }

        AttachingConnector connector =
            (AttachingConnector) findConnector("com.sun.jdi.SocketAttach");

        Map<java.lang.String,? extends com.sun.jdi.connect.Connector.Argument> cArgs = connector.defaultArguments();
        Iterator cArgsValIter = cArgs.keySet().iterator();
        while (cArgsValIter.hasNext()) {
            String argKey = (String) cArgsValIter.next();
            String argVal = null;

            if ((arg = (Connector.Argument) cArgs.get(argKey)) == null) {
                log.complain("Argument " + argKey.toString() +
                    "is not defined for the connector: " + connector.name());
            }
            if (arg.name().equals("hostname") && host != null)
                arg.setValue(host);
            if (arg.name().equals("port"))
                arg.setValue(port);

            log.display("\targument name=" + arg.name());
            if ((argVal = arg.value()) != null)
                log.display("\t\tvalue="+argVal);
            else log.display("\t\tvalue=NULL");
        }

        for (int i = 0; i < attempts; i++) {
            try {
                return connector.attach(cArgs);
            } catch (IOException e) {
                log.display("Connection attempt #" + i + " failed: " + e);
                try {
                    Thread.currentThread().sleep(delay);
                } catch (InterruptedException ie) {
                    log.complain("TEST INCOMPLETE: interrupted sleep: " + ie);
                }
            } catch (IllegalConnectorArgumentsException e) {
                log.complain("TEST: Illegal connector arguments: " +
                    e.getMessage());
                return null;
            } catch (Exception e) {
                log.complain("TEST: Internal error: " + e.getMessage());
                return null;
            }
        }
        log.complain("FAILURE: all attempts to connect to the debugee VM failed");
        return null;
    }

    private Connector findConnector(String connectorName) {
        List connectors = Bootstrap.virtualMachineManager().allConnectors();
        Iterator iter = connectors.iterator();

        while (iter.hasNext()) {
            Connector connector = (Connector) iter.next();
            if (connector.name().equals(connectorName)) {
                log.display("Connector name=" + connector.name() +
                    "\n\tdescription=" + connector.description() +
                    "\n\ttransport=" + connector.transport().name());
                return connector;
            }
        }
        throw new Error("No appropriate connector");
    }
}
