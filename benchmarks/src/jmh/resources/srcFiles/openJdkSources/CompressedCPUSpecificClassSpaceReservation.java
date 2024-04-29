/*
 * Copyright (c) 2013, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test the various CPU-specific reservation schemes
 * @requires vm.bits == 64 & !vm.graal.enabled & vm.debug == true
 * @requires vm.flagless
 * @requires (os.family != "windows") & (os.family != "aix")
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run driver CompressedCPUSpecificClassSpaceReservation
 */

import jdk.test.lib.Platform;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jtreg.SkippedException;

import java.io.IOException;

public class CompressedCPUSpecificClassSpaceReservation {

    private static void do_test(boolean CDS) throws IOException {
        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(
                "-Xshare:" + (CDS ? "on" : "off"),
                "-Xmx128m",
                "-XX:CompressedClassSpaceSize=128m",
                "-Xlog:metaspace*", "-Xlog:metaspace+map=trace", "-Xlog:os+map=trace",
                "-XX:+SimulateFullAddressSpace", 
                "-version");
        OutputAnalyzer output = new OutputAnalyzer(pb.start());

        final String tryReserveForUnscaled = "reserve_between (range [0x0000000000000000-0x0000000100000000)";
        final String tryReserveForZeroBased = "reserve_between (range [0x0000000100000000-0x0000000800000000)";
        final String tryReserveFor16bitMoveIntoQ3 = "reserve_between (range [0x0000000100000000-0x0001000000000000)";
        if (Platform.isAArch64()) {
            if (CDS) {
                output.shouldNotContain(tryReserveForUnscaled);
            } else {
                output.shouldContain(tryReserveForUnscaled);
            }
            output.shouldContain("Trying to reserve at an EOR-compatible address");
            output.shouldNotContain(tryReserveForZeroBased);
            output.shouldContain(tryReserveFor16bitMoveIntoQ3);
        } else if (Platform.isPPC()) {
            if (CDS) {
                output.shouldNotContain(tryReserveForUnscaled);
                output.shouldNotContain(tryReserveForZeroBased);
            } else {
                output.shouldContain(tryReserveForUnscaled);
                output.shouldContain(tryReserveForZeroBased);
            }
            output.shouldContain(tryReserveFor16bitMoveIntoQ3);
        } else if (Platform.isRISCV64()) {
            output.shouldContain(tryReserveForUnscaled); 
            if (CDS) {
                output.shouldNotContain(tryReserveForZeroBased);
                output.shouldContain("reserve_between (range [0x0000000100000000-0x0000100000000000)");
            } else {
                output.shouldContain(tryReserveForZeroBased);
                output.shouldContain("reserve_between (range [0x0000000800000000-0x0000100000000000)");
            }
            output.shouldContain("reserve_between (range [0x0000100000000000-0xffffffffffffffff)");
        } else if (Platform.isS390x()) {
            output.shouldContain(tryReserveForUnscaled); 
            if (CDS) {
                output.shouldNotContain(tryReserveForZeroBased);
            } else {
                output.shouldContain(tryReserveForZeroBased);
            }
            output.shouldContain(tryReserveFor16bitMoveIntoQ3);
        } else if (Platform.isX64()) {
            if (CDS) {
                output.shouldNotContain(tryReserveForUnscaled);
                output.shouldNotContain(tryReserveForZeroBased);
            } else {
                output.shouldContain(tryReserveForUnscaled);
                output.shouldContain(tryReserveForZeroBased);
            }
        } else {
            throw new RuntimeException("Unexpected platform");
        }

        if (CDS) {
            output.shouldContain("CDS archive(s) mapped at:");
        } else {
            output.shouldContain("CDS archive(s) not mapped");
        }
        output.shouldContain("Compressed class space mapped at:");
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Test with CDS");
        do_test(true);
        System.out.println("Test without CDS");
        do_test(false);
    }
}
