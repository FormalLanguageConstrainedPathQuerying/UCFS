/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.concurrent.CountDownLatch;

/**
 * Test native threads attaching implicitly to the VM by means of an upcall.
 */
public class ImplicitAttach {
    private static final ValueLayout.OfInt C_INT = ValueLayout.JAVA_INT;
    private static final AddressLayout C_POINTER = ValueLayout.ADDRESS;

    private static volatile CountDownLatch latch;

    public static void main(String[] args) throws Throwable {
        int threadCount;
        if (args.length > 0) {
            threadCount = Integer.parseInt(args[0]);
        } else {
            threadCount = 2;
        }
        latch = new CountDownLatch(threadCount);

        Linker abi;
        try {
            abi = Linker.nativeLinker();
        } catch (UnsupportedOperationException e) {
            System.out.println("Test skipped, no native linker on this platform");
            return;
        }

        MethodHandle callback = MethodHandles.lookup()
                .findStatic(ImplicitAttach.class, "callback", MethodType.methodType(void.class));
        MemorySegment upcallStub = abi.upcallStub(callback,
                FunctionDescriptor.ofVoid(),
                Arena.global());

        SymbolLookup symbolLookup = SymbolLookup.loaderLookup();
        MemorySegment symbol = symbolLookup.find("start_threads").orElseThrow();
        FunctionDescriptor desc = FunctionDescriptor.ofVoid(C_INT, C_POINTER);
        MethodHandle start_threads = abi.downcallHandle(symbol, desc);

        start_threads.invoke(threadCount, upcallStub);
        latch.await();
    }

    /**
     * Invoked from native thread.
     */
    private static void callback() {
        System.out.println(Thread.currentThread());
        latch.countDown();
    }

    static {
        System.loadLibrary("ImplicitAttach");
    }
}
