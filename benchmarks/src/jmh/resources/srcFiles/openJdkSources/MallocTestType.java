/*
 * Copyright (c) 2014, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, Red Hat, Inc. All rights reserved.
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
 * @summary Test consistency of NMT by leaking a few select allocations of the Test type and then verify visibility with jcmd
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI -XX:NativeMemoryTracking=detail MallocTestType
 */

import jdk.test.whitebox.WhiteBox;

public class MallocTestType {

  public static void main(String args[]) throws Exception {
    WhiteBox wb = WhiteBox.getWhiteBox();

    long memAlloc3 = wb.NMTMalloc(128 * 1024);  
    long memAlloc2 = wb.NMTMalloc(256 * 1024);  

    NMTTestUtils.runJcmdSummaryReportAndCheckOutput(
            new String[]{"Test (reserved=384KB, committed=384KB)",
                         "(malloc=384KB #2) (at peak)"});

    wb.NMTFree(memAlloc3);                           
    long memAlloc1 = wb.NMTMalloc(512 * 1024);  
    wb.NMTFree(memAlloc2);                           

    NMTTestUtils.runJcmdSummaryReportAndCheckOutput(
            new String[]{"Test (reserved=512KB, committed=512KB)",
                         "(malloc=512KB #1) (peak=768KB #2)"});

    wb.NMTFree(memAlloc1); 

    NMTTestUtils.runJcmdSummaryReportAndCheckOutput(
            new String[]{"Test (reserved=0KB, committed=0KB)",
                         "(malloc=0KB) (peak=768KB #2)"});
  }
}
