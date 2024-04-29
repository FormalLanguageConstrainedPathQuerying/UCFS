/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
 * @test TestOnSpinWaitAArch64
 * @summary Checks that java.lang.Thread.onSpinWait is intrinsified with instructions specified with '-XX:OnSpinWaitInst' and '-XX:OnSpinWaitInstCount'
 * @bug 8186670
 * @library /test/lib
 *
 * @requires vm.flagless
 * @requires os.arch=="aarch64"
 *
 * @run driver compiler.onSpinWait.TestOnSpinWaitAArch64 c2 nop 7
 * @run driver compiler.onSpinWait.TestOnSpinWaitAArch64 c2 isb 3
 * @run driver compiler.onSpinWait.TestOnSpinWaitAArch64 c2 yield 1
 * @run driver compiler.onSpinWait.TestOnSpinWaitAArch64 c1 nop 7
 * @run driver compiler.onSpinWait.TestOnSpinWaitAArch64 c1 isb 3
 * @run driver compiler.onSpinWait.TestOnSpinWaitAArch64 c1 yield
 */

package compiler.onSpinWait;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.ListIterator;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class TestOnSpinWaitAArch64 {
    public static void main(String[] args) throws Exception {
        String compiler = args[0];
        String spinWaitInst = args[1];
        String spinWaitInstCount = (args.length == 3) ? args[2] : "1";
        ArrayList<String> command = new ArrayList<String>();
        command.add("-XX:+IgnoreUnrecognizedVMOptions");
        command.add("-showversion");
        command.add("-XX:-BackgroundCompilation");
        command.add("-XX:+UnlockDiagnosticVMOptions");
        command.add("-XX:+PrintAssembly");
        if (compiler.equals("c2")) {
            command.add("-XX:-TieredCompilation");
        } else if (compiler.equals("c1")) {
            command.add("-XX:+TieredCompilation");
            command.add("-XX:TieredStopAtLevel=1");
        } else {
            throw new RuntimeException("Unknown compiler: " + compiler);
        }
        command.add("-Xbatch");
        command.add("-XX:OnSpinWaitInst=" + spinWaitInst);
        command.add("-XX:OnSpinWaitInstCount=" + spinWaitInstCount);
        command.add("-XX:CompileCommand=compileonly," + Launcher.class.getName() + "::" + "test");
        command.add(Launcher.class.getName());

        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(command);

        OutputAnalyzer analyzer = new OutputAnalyzer(pb.start());

        analyzer.shouldHaveExitValue(0);

        System.out.println(analyzer.getOutput());

        checkOutput(analyzer, spinWaitInst, Integer.parseInt(spinWaitInstCount));
    }

    private static String getSpinWaitInstHex(String spinWaitInst) {
      if ("nop".equals(spinWaitInst)) {
          return "1f20 03d5";
      } else if ("isb".equals(spinWaitInst)) {
          return "df3f 03d5";
      } else if ("yield".equals(spinWaitInst)) {
          return "3f20 03d5";
      } else {
          throw new RuntimeException("Unknown spin wait instruction: " + spinWaitInst);
      }
    }

    private static void addInstrs(String line, ArrayList<String> instrs) {
        for (String instr : line.split("\\|")) {
            instrs.add(instr.trim());
        }
    }

    private static void checkOutput(OutputAnalyzer output, String spinWaitInst, int spinWaitInstCount) {
        Iterator<String> iter = output.asLines().listIterator();

        String match = skipTo(iter, "'test' '()V' in 'compiler/onSpinWait/TestOnSpinWaitAArch64$Launcher'");
        if (match == null) {
            throw new RuntimeException("Missing compiler output for the method compiler.onSpinWait.TestOnSpinWaitAArch64$Launcher::test");
        }

        ArrayList<String> instrs = new ArrayList<String>();
        String line = null;
        boolean hasHexInstInOutput = false;
        while (iter.hasNext()) {
            line = iter.next();
            if (line.contains("*invokestatic onSpinWait")) {
                break;
            }
            if (!hasHexInstInOutput) {
                hasHexInstInOutput = line.contains("|");
            }
            if (line.contains("0x") && !line.contains(";")) {
                addInstrs(line, instrs);
            }
        }

        if (!iter.hasNext() || !iter.next().contains("- compiler.onSpinWait.TestOnSpinWaitAArch64$Launcher::test@0") || !iter.hasNext()) {
            throw new RuntimeException("Missing compiler output for Thread.onSpinWait intrinsic");
        }

        String strToSearch = null;
        if (!hasHexInstInOutput) {
            instrs.add(line.split(";")[0].trim());
            strToSearch = spinWaitInst;
        } else {
            line = iter.next();
            if (!line.contains("0x") || line.contains(";")) {
                throw new RuntimeException("Expected hex instructions");
            }

            addInstrs(line, instrs);
            strToSearch = getSpinWaitInstHex(spinWaitInst);
        }

        int foundInstCount = 0;

        ListIterator<String> instrReverseIter = instrs.listIterator(instrs.size());
        while (instrReverseIter.hasPrevious()) {
            if (instrReverseIter.previous().endsWith(strToSearch)) {
                foundInstCount = 1;
                break;
            }
        }

        while (instrReverseIter.hasPrevious()) {
            if (!instrReverseIter.previous().endsWith(strToSearch)) {
                break;
            }
            ++foundInstCount;
        }

        if (foundInstCount != spinWaitInstCount) {
            throw new RuntimeException("Wrong instruction " + strToSearch + " count " + foundInstCount + "!\n  -- expecting " + spinWaitInstCount);
        }
    }

    private static String skipTo(Iterator<String> iter, String substring) {
        while (iter.hasNext()) {
            String nextLine = iter.next();
            if (nextLine.contains(substring)) {
                return nextLine;
            }
        }
        return null;
    }

    static class Launcher {
        public static void main(final String[] args) throws Exception {
            int end = 20_000;

            for (int i=0; i < end; i++) {
                test();
            }
        }
        static void test() {
            java.lang.Thread.onSpinWait();
        }
    }
}
