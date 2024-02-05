/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8262271
 * @requires vm.hasSA
 * @library /test/lib
 * @run main/timeout=240 JStackStressTest
 */

import java.io.IOException;
import java.io.OutputStream;

import jdk.test.lib.JDKToolLauncher;
import jdk.test.lib.JDKToolFinder;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.SA.SATestUtils;
import jdk.test.lib.Utils;

public class JStackStressTest {

    static Process jShellProcess;

    public static void testjstack() throws IOException {
        launchJshell();
        long jShellPID = jShellProcess.pid();
        OutputAnalyzer jshellOutput = new OutputAnalyzer(jShellProcess);

        try {
            for (int i = 1; i <= 4; i++) {
                JDKToolLauncher launcher = JDKToolLauncher.createUsingTestJDK("jhsdb");
                launcher.addVMArgs(Utils.getTestJavaOpts());
                launcher.addToolArg("jstack");
                launcher.addToolArg("--pid=" + Long.toString(jShellPID));

                System.out.println("###### Starting jstack iteration " + i + " against " + jShellPID);
                long startTime = System.currentTimeMillis();
                ProcessBuilder processBuilder = SATestUtils.createProcessBuilder(launcher);
                OutputAnalyzer output = ProcessTools.executeProcess(processBuilder);
                System.out.println("jhsdb jstack stdout:");
                System.out.println(output.getStdout());
                System.out.println("jhsdb jstack stderr:");
                System.out.println(output.getStderr());
                long elapsedTime = System.currentTimeMillis() - startTime;
                System.out.println("###### End of all output for iteration " + i +
                                   " which took " + elapsedTime + "ms");
                output.shouldHaveExitValue(0);
                output.shouldNotMatch("^sun.jvm.hotspot.debugger.DebuggerException:.*$");
                output.shouldNotMatch("^Error: .*$");
            }
        } catch (Exception ex) {
            throw new RuntimeException("Test ERROR " + ex, ex);
        } finally {
            try (OutputStream out = jShellProcess.getOutputStream()) {
                out.write("/exit\n".getBytes());
                out.flush();
            }
            try {
                jShellProcess.waitFor(); 
            } catch (InterruptedException e) {
            }
            System.out.println("jshell Output: " + jshellOutput.getOutput());
        }
    }

    public static void launchJshell() throws IOException {
        System.out.println("Starting Jshell");
        long startTime = System.currentTimeMillis();
        try {
            JDKToolLauncher launcher = JDKToolLauncher.createUsingTestJDK("jshell");
            launcher.addVMArgs(Utils.getTestJavaOpts());
            ProcessBuilder pb = new ProcessBuilder(launcher.getCommand());
            jShellProcess = ProcessTools.startProcess("JShell", pb);
        } catch (Exception ex) {
            throw new RuntimeException("Test ERROR " + ex, ex);
        }
    }

    public static void main(String[] args) throws Exception {
        SATestUtils.skipIfCannotAttach(); 
        testjstack();

        System.out.println("Test PASSED");
    }
}
