/*
 * Copyright (c) 2017, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.regex.Pattern;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.Platform;
import jdk.test.lib.process.ProcessTools;

/*
 * @test
 * @requires vm.flagless
 * @requires (vm.debug == true)
 * @bug 8167108
 * @summary Nested ThreadsListHandle info should be in error handling output.
 * @modules java.base/jdk.internal.misc
 * @library /test/lib
 * @run driver NestedThreadsListHandleInErrorHandlingTest
 */

/*
 * This test was created using SafeFetchInErrorHandlingTest.java
 * as a guide.
 */
public class NestedThreadsListHandleInErrorHandlingTest {
  public static void main(String[] args) throws Exception {

    ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(
        "-XX:+UnlockDiagnosticVMOptions",
        "-XX:+EnableThreadSMRStatistics",
        "-Xmx100M",
        "-XX:ErrorHandlerTest=17",
        "-XX:-CreateCoredumpOnCrash",
        "-XX:-ShowRegistersOnAssert",
        "-version");

    OutputAnalyzer output_detail = new OutputAnalyzer(pb.start());

    output_detail.shouldMatch("# A fatal error has been detected by the Java Runtime Environment:.*");
    System.out.println("Found fatal error header.");
    output_detail.shouldMatch("# +fatal error: Force crash with a nested ThreadsListHandle.");
    System.out.println("Found specific fatal error.");

    File hs_err_file = HsErrFileUtils.openHsErrFileFromOutput(output_detail);

    Pattern [] pattern = new Pattern[] {
        Pattern.compile("Current thread .* _threads_hazard_ptr=0x[0-9A-Fa-f][0-9A-Fa-f]*, _nested_threads_hazard_ptr_cnt=1, _nested_threads_hazard_ptr=0x[0-9A-Fa-f][0-9A-Fa-f]*.*"),
        Pattern.compile("Threads class SMR info:"),
        Pattern.compile(".*, _nested_thread_list_max=2"),
        Pattern.compile("=>.* JavaThread \"main\" .*, _nested_threads_hazard_ptr_cnt=1, _nested_threads_hazard_ptr=0x[0-9A-Fa-f][0-9A-Fa-f]*.*"),
    };

    HsErrFileUtils.checkHsErrFileContent(hs_err_file, pattern, false);

    System.out.println("PASSED.");
  }
}
