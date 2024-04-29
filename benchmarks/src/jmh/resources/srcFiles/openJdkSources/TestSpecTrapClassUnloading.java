/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8031752
 * @summary speculative traps need to be cleaned up at GC
 *
 * @run main/othervm -XX:+IgnoreUnrecognizedVMOptions -XX:-TieredCompilation
 *                   -XX:-UseOnStackReplacement -XX:-BackgroundCompilation
 *                   -XX:CompileThreshold=10000
 *                   -XX:+UseTypeSpeculation -XX:TypeProfileLevel=222
 *                   -XX:CompileCommand=exclude,java.lang.reflect.Method::invoke
 *                   -XX:CompileCommand=exclude,sun.reflect.DelegatingMethodAccessorImpl::invoke
 *                   -Xmx512M
 *                   compiler.profiling.TestSpecTrapClassUnloading
 */

package compiler.profiling;

import java.lang.reflect.Method;

public class TestSpecTrapClassUnloading {
    static class B {
        final public boolean m(Object o) {
            if (o.getClass() == B.class) {
                return true;
            }
            return false;
        }
    }

    static class MemoryChunk {
        MemoryChunk other;
        long[] array;
        MemoryChunk(MemoryChunk other) {
            this.other = other;
            array = new long[1024 * 1024 * 1024];
        }
    }

    static void m1(B b, Object o) {
        b.m(o);
    }

    static void m2(B b, Object o) {
        b.m(o);
    }

    public static void main(String[] args) throws Exception {
        Method m = B.class.getMethod("m", Object.class);
        Object o = new Object();
        B b = new B();

        for (int i = 0; i < 20000; i++) {
            m1(b, b);
        }
        m1(b, o);

        for (int i = 0; i < 20000; i++) {
            m.invoke(b, b);
        }
        m.invoke(b, o);

        m = null;

        for (int i = 0; i < 20000; i++) {
            m2(b, b);
        }
        m2(b, o);

        MemoryChunk root = null;
        try {
            while (true) {
                root = new MemoryChunk(root);
            }
        } catch(OutOfMemoryError e) {
            root = null;
        }
    }
}
