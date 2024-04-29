/*
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8035473 8182765
 * @summary make sure tool is quiet when told to, and chatty otherwise
 */


import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Dummy javadoc comment.
 */
public class QuietOption {

    final File javadoc;
    final File testSrc;
    final String thisClassName;

    /**
     * Dummy javadoc comment.
     */
    public QuietOption() {
        File javaHome = new File(System.getProperty("java.home"));
        if (javaHome.getName().endsWith("jre"))
            javaHome = javaHome.getParentFile();
        javadoc = new File(new File(javaHome, "bin"), "javadoc");
        testSrc = new File(System.getProperty("test.src"));
        thisClassName = QuietOption.class.getName();
    }

    /**
     * Dummy javadoc comment.
     * @param args dummy
     * @throws Exception if error
     */
    public static void main(String... args) throws Exception {
        QuietOption test = new QuietOption();
        test.run1();
        test.run2();
    }

    void run1() throws Exception {
        List<String> output = doTest(javadoc.getPath(),
                "-classpath", ".", 
                "-quiet",
                new File(testSrc, thisClassName + ".java").getPath());

        if (!output.isEmpty()) {
            Iterator<String> iter = output.iterator();
            while (iter.hasNext()) {
                String line = iter.next();
                if (line.matches("^Picked up .*JAVA.*OPTIONS:.*")) {
                    System.out.println("IGNORING: " + line);
                    iter.remove();
                }
            }
        }

        if (!output.isEmpty()) {
            System.out.println(output);
            throw new Exception("run1: Shhh!, very chatty javadoc!.");
        }
    }

    void run2() throws Exception {
        List<String> output = doTest(javadoc.getPath(),
                "-classpath", ".", 
                new File(testSrc, thisClassName + ".java").getPath());

        if (output.isEmpty()) {
            System.out.println(output);
            throw new Exception("run2: speak up and please be heard!.");
        }
    }

    /**
     * More dummy comments.
     */
    List<String> doTest(String... args) throws Exception {
        List<String> output = new ArrayList<>();
        Process p = new ProcessBuilder()
                .command(args)
                .redirectErrorStream(true)
                .start();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line = in.readLine();
            while (line != null) {
                output.add(line.trim());
                line = in.readLine();
            }
        }
        int rc = p.waitFor();
        if (rc != 0) {
            throw new Exception("javadoc failed, rc:" + rc);
        }
        return output;
    }
}
