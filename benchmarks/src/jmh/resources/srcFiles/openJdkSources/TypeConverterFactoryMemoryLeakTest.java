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

/*
 * @test id=with_SerialGC
 * @requires vm.gc.Serial
 * @bug 8198540
 * @summary Test TypeConverterFactory is not leaking method handles (Serial GC)
 * @run main/othervm -XX:+UseSerialGC TypeConverterFactoryMemoryLeakTest
 */

/*
 * @test id=with_ParallelGC
 * @requires vm.gc.Parallel
 * @bug 8198540
 * @summary Test TypeConverterFactory is not leaking method handles (Parallel GC)
 * @run main/othervm -XX:+UseParallelGC TypeConverterFactoryMemoryLeakTest
 */

/*
 * @test id=with_G1GC
 * @requires vm.gc.G1
 * @bug 8198540
 * @summary Test TypeConverterFactory is not leaking method handles (G1 GC)
 * @run main/othervm -XX:+UseG1GC TypeConverterFactoryMemoryLeakTest
 */

/*
 * @test id=with_ZGC_Singlegen
 * @requires vm.gc.ZSinglegen
 * @bug 8198540
 * @summary Test TypeConverterFactory is not leaking method handles (Z GC)
 * @run main/othervm -XX:+UseZGC -XX:-ZGenerational TypeConverterFactoryMemoryLeakTest
 */

/*
 * @test id=with_ZGC_Generational
 * @requires vm.gc.ZGenerational
 * @bug 8198540
 * @summary Test TypeConverterFactory is not leaking method handles (Z GC)
 * @run main/othervm -XX:+UseZGC -XX:+ZGenerational TypeConverterFactoryMemoryLeakTest
 */

/*
 * @test id=with_ShenandoahGC
 * @requires vm.gc.Shenandoah
 * @bug 8198540
 * @summary Test TypeConverterFactory is not leaking method handles (Shenandoah GC)
 * @run main/othervm -XX:+UseShenandoahGC TypeConverterFactoryMemoryLeakTest
 */

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import jdk.dynalink.DynamicLinker;
import jdk.dynalink.DynamicLinkerFactory;
import jdk.dynalink.linker.GuardedInvocation;
import jdk.dynalink.linker.GuardingDynamicLinker;
import jdk.dynalink.linker.GuardingTypeConverterFactory;
import jdk.dynalink.linker.LinkRequest;
import jdk.dynalink.linker.LinkerServices;

/**
 * Tests that converter method handles created by
 * jdk.dynalink.TypeConverterFactory become unreachable when the factory itself
 * becomes unreachable.
 */
public class TypeConverterFactoryMemoryLeakTest {
    private static final int MAX_ITERATIONS = 1000;

    private static final ReferenceQueue<MethodHandle> refQueue = new ReferenceQueue<>();
    private static final List<Reference<MethodHandle>> refs = new ArrayList<>();

    private static class TestLinker implements GuardingDynamicLinker, GuardingTypeConverterFactory {
        public GuardedInvocation getGuardedInvocation(LinkRequest linkRequest, LinkerServices linkerServices) {
            throw new UnsupportedOperationException();
        }

        public GuardedInvocation convertToType(Class<?> sourceType, Class<?> targetType, Supplier<MethodHandles.Lookup> lookupSupplier) {
            MethodHandle result = MethodHandles.empty(MethodType.methodType(targetType, sourceType));
            refs.add(new PhantomReference<>(result, refQueue));
            return new GuardedInvocation(result);
        }
    }

    public static void main(String[] args) {
        for (int count = 0; count < MAX_ITERATIONS; count++) {
            makeOne();
            System.gc();
            if (refQueue.poll() != null) {
                return;
            }
        }
        throw new AssertionError("Should have GCd a method handle by now");
    }

    private static void makeOne() {
        DynamicLinkerFactory f = new DynamicLinkerFactory();
        f.setFallbackLinkers();
        f.setPrioritizedLinker(new TestLinker());
        DynamicLinker linker = f.createLinker();
        linker.getLinkerServices().getTypeConverter(double.class, int.class);
    }
}
