/*
 * Copyright (c) 2014, 2023, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8022865
 * @summary Tests for different combination of UseCompressedOops options
 * @requires vm.flagless
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm/timeout=480 -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -Xbootclasspath/a:. UseCompressedOops
 */
import java.util.ArrayList;
import java.util.Collections;
import jdk.test.lib.Platform;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.whitebox.gc.GC;

import jdk.test.whitebox.code.Compiler;

public class UseCompressedOops {

    public static void main(String[] args) throws Exception {
        testCompressedOopsModesGCs();
        if (!Platform.isOSX() && !Platform.isAix()) {
            testCompressedOopsModesGCs("-XX:+UseLargePages");
        }
    }

    public static void testCompressedOopsModesGCs(String... flags) throws Exception {
        ArrayList<String> args = new ArrayList<>();
        Collections.addAll(args, flags);

        testCompressedOopsModes(args);
        testCompressedOopsModes(args, "-XX:+UseG1GC");
        testCompressedOopsModes(args, "-XX:+UseSerialGC");
        testCompressedOopsModes(args, "-XX:+UseParallelGC");
        if (GC.Shenandoah.isSupported()) {
            testCompressedOopsModes(args, "-XX:+UnlockExperimentalVMOptions", "-XX:+UseShenandoahGC");
        }
    }

    public static void testCompressedOopsModes(ArrayList<String> flags1, String... flags2) throws Exception {
        ArrayList<String> args = new ArrayList<>();
        args.addAll(flags1);
        Collections.addAll(args, flags2);

        if (Platform.is64bit()) {
            testCompressedOops(args, "-XX:-UseCompressedOops", "-Xmx32m")
                .shouldNotContain("Compressed Oops")
                .shouldHaveExitValue(0);

            testCompressedOops(args, "-Xmx32m")
                .shouldContain("Compressed Oops mode")
                .shouldHaveExitValue(0);

            testCompressedOops(args, "-XX:+UseCompressedOops", "-Xmx32m")
                .shouldContain("Compressed Oops mode")
                .shouldHaveExitValue(0);

            if (!Platform.isOSX() && !Platform.isWindows()) {

                testCompressedOops(args, "-XX:+UseCompressedOops", "-Xmx5g")
                    .shouldContain("Zero based")
                    .shouldContain("Oop shift amount: 3")
                    .shouldHaveExitValue(0);

                testCompressedOops(args, "-XX:+UseCompressedOops", "-Xmx3200m", "-XX:HeapBaseMinAddress=1g")
                    .shouldContain("Zero based")
                    .shouldContain("Oop shift amount: 3")
                    .shouldHaveExitValue(0);

                testCompressedOops(args, "-XX:+UseCompressedOops", "-Xmx32m", "-XX:HeapBaseMinAddress=4g")
                    .shouldContain("Zero based")
                    .shouldContain("Oop shift amount: 3")
                    .shouldHaveExitValue(0);

                testCompressedOops(args, "-XX:+UseCompressedOops", "-Xmx32m", "-XX:HeapBaseMinAddress=32g")
                    .shouldContain("Non-zero disjoint base")
                    .shouldContain("Oop shift amount: 3")
                    .shouldHaveExitValue(0);

                testCompressedOops(args, "-XX:+UseCompressedOops", "-Xmx32m", "-XX:HeapBaseMinAddress=72704m")
                    .shouldContain("Non-zero based")
                    .shouldContain("Oop shift amount: 3")
                    .shouldHaveExitValue(0);

                testCompressedOops(args, "-XX:+UseCompressedOops", "-Xmx32g", "-XX:ObjectAlignmentInBytes=16",
                               "-XX:HeapBaseMinAddress=64g")
                    .shouldContain("Non-zero disjoint base")
                    .shouldContain("Oop shift amount: 4")
                    .shouldHaveExitValue(0);

                testCompressedOops(args, "-XX:+UseCompressedOops", "-Xmx32g", "-XX:ObjectAlignmentInBytes=16")
                    .shouldContain("Zero based")
                    .shouldContain("Oop shift amount: 4")
                    .shouldHaveExitValue(0);
            }

            testCompressedOops(args, "-XX:+UseCompressedOops", "-Xmx2g")
                .shouldNotContain("Max heap size too large for Compressed Oops")
                .shouldHaveExitValue(0);
            testCompressedOops(args, "-XX:+UseCompressedOops", "-Xmx29g", "-XX:CompressedClassSpaceSize=1g")
                .shouldNotContain("Max heap size too large for Compressed Oops")
                .shouldHaveExitValue(0);

            testCompressedOops(args, "-XX:+UseCompressedOops", "-Xmx32g")
                .shouldContain("Max heap size too large for Compressed Oops")
                .shouldHaveExitValue(0);

            testCompressedOops(args, "-Xmx32g")
                .shouldNotContain("Max heap size too large for Compressed Oops")
                .shouldHaveExitValue(0);

            testCompressedOops(args, "-XX:+UseCompressedOops", "-Xmx32g", "-XX:ObjectAlignmentInBytes=8")
                .shouldContain("Max heap size too large for Compressed Oops")
                .shouldHaveExitValue(0);

            testCompressedOops(args, "-XX:+UseCompressedOops", "-Xmx64g", "-XX:ObjectAlignmentInBytes=16")
                .shouldContain("Max heap size too large for Compressed Oops")
                .shouldHaveExitValue(0);

        } else {
            testCompressedOops(args, "-XX:+UseCompressedOops", "-Xmx32m")
                .shouldContain("Unrecognized VM option 'UseCompressedOops'")
                .shouldHaveExitValue(1);
        }
    }

    private static OutputAnalyzer testCompressedOops(ArrayList<String> flags1, String... flags2) throws Exception {
        ArrayList<String> args = new ArrayList<>();

        args.add("-Xlog:gc+heap+coops=trace");
        args.add("-Xms32m");

        args.addAll(flags1);
        Collections.addAll(args, flags2);

        args.add("-version");

        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(args);
        return new OutputAnalyzer(pb.start());
    }
}
