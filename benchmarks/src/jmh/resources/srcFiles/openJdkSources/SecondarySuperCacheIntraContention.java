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

package org.openjdk.bench.vm.compiler;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

@Warmup(iterations = 5, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Fork(value = 3, jvmArgsAppend = {"-XX:+TieredCompilation", "-XX:TieredStopAtLevel=1"})
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Threads(Threads.MAX)
@State(Scope.Benchmark)
public class SecondarySuperCacheIntraContention {



    interface IA {}
    interface IB {}
    class B {}
    class C1 extends B implements IA, IB {}
    class C2 extends B implements IA, IB {}

    volatile B o1, o2;

    @Setup
    public void setup() {
        o1 = new C1();
        o2 = new C2();
    }

    @Benchmark
    @OperationsPerInvocation(4)
    public void test(Blackhole bh) {
        bh.consume(o1 instanceof IA);
        bh.consume(o2 instanceof IA);
        bh.consume(o1 instanceof IB);
        bh.consume(o2 instanceof IB);
    }

}
