/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.event.gc.detailed;

import java.util.List;

import static gc.testlibrary.Allocation.blackHole;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.test.lib.jfr.EventNames;
import jdk.test.lib.jfr.Events;

/**
 * @test id=ZSinglegen
 * @requires vm.hasJFR & vm.gc.ZSinglegen
 * @key jfr
 * @library /test/lib /test/jdk /test/hotspot/jtreg
 * @run main/othervm -XX:+UseZGC -XX:-ZGenerational -Xmx32M jdk.jfr.event.gc.detailed.TestZUnmapEvent
 */

/**
 * @test id=ZGenerational
 * @requires vm.hasJFR & vm.gc.ZGenerational
 * @key jfr
 * @library /test/lib /test/jdk /test/hotspot/jtreg
 * @run main/othervm -XX:+UseZGC -XX:+ZGenerational -Xmx32M jdk.jfr.event.gc.detailed.TestZUnmapEvent
 */

public class TestZUnmapEvent {
    public static void main(String[] args) throws Exception {
        try (Recording recording = new Recording()) {
            recording.enable(EventNames.ZUnmap);
            recording.start();

            for (int i = 0; i < 128; i++) {
                blackHole(new byte[256 * 1024]);
            }

            for (int i = 0; i < 10; i++) {
                blackHole(new byte[7 * 1024 * 1024]);
            }

            Thread.sleep(10 * 1000);

            recording.stop();

            List<RecordedEvent> events = Events.fromRecording(recording);
            System.out.println("Events: " + events.size());
            Events.hasEvents(events);
        }
    }
}
