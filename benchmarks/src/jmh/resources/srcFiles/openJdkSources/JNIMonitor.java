/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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
import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
 * Tests that JNI monitors work correctly with virtual threads,
 * There are multiple test scenarios that we check using unified logging output
 * (both positive and negative tests). Each test case is handled by its own @-test
 * definition so that we can run each sub-test independently.
 *
 * The original bug was only discovered because the ForkJoinPool worker thread terminated
 * and trigerred an assertion failure. So we use a custom scheduler to give us control.
 */

/**
 * @test id=normal
 * @bug 8327743
 * @summary Normal lock then unlock
 * @library /test/lib
 * @modules java.base/java.lang:+open
 * @requires vm.continuations
 * @run driver JNIMonitor Normal
 */

/**
 * @test id=multiNormal
 * @bug 8327743
 * @summary Normal lock then unlock by multiple threads
 * @library /test/lib
 * @modules java.base/java.lang:+open
 * @requires vm.continuations
 * @run driver JNIMonitor MultiNormal
 */

/**
 * @test id=missingUnlock
 * @bug 8327743
 * @summary Don't do the unlock and exit normally
 * @library /test/lib
 * @modules java.base/java.lang:+open
 * @requires vm.continuations
 * @run driver JNIMonitor MissingUnlock
 */

/**
 * @test id=multiMissingUnlock
 * @bug 8327743
 * @summary Don't do the unlock and exit normally, by multiple threads
 * @library /test/lib
 * @modules java.base/java.lang:+open
 * @requires vm.continuations
 * @run driver JNIMonitor MultiMissingUnlock
 */

/**
 * @test id=missingUnlockWithThrow
 * @bug 8327743
 * @summary Don't do the unlock and exit by throwing
 * @library /test/lib
 * @modules java.base/java.lang:+open
 * @requires vm.continuations
 * @run driver JNIMonitor MissingUnlockWithThrow
 */

/**
 * @test id=multiMissingUnlockWithThrow
 * @bug 8327743
 * @summary Don't do the unlock and exit by throwing, by multiple threads
 * @library /test/lib
 * @modules java.base/java.lang:+open
 * @requires vm.continuations
 * @run driver JNIMonitor MultiMissingUnlockWithThrow
 */

public class JNIMonitor {

    public static void main(String[] args) throws Exception {
        String test = args[0];
        String[] cmdArgs = new String[] {
            "-Djava.library.path=" + Utils.TEST_NATIVE_PATH,
            "--add-opens=java.base/java.lang=ALL-UNNAMED",
            "-Xcheck:jni",
            "-Xlog:jni=debug",
            "-Xlog:thread+os=info",
            "JNIMonitor$" + test,
        };
        OutputAnalyzer oa = ProcessTools.executeTestJava(cmdArgs);
        oa.shouldHaveExitValue(0);
        oa.stdoutShouldMatch(terminated);

        switch(test) {
            case "Normal":
            case "MultiNormal":
                oa.stdoutShouldNotMatch(stillLocked);
                break;
            case "MissingUnlock":
                oa.stdoutShouldMatch(stillLocked);
                break;
            case "MultiMissingUnlock":
                parseOutputForPattern(oa.stdoutAsLines(), stillLocked, MULTI_THREAD_COUNT);
                break;
            case "MissingUnlockWithThrow":
                oa.stdoutShouldMatch(stillLocked);
                oa.stderrShouldContain(throwMsg);
                break;
            case "MultiMissingUnlockWithThrow":
                parseOutputForPattern(oa.stdoutAsLines(), stillLocked, MULTI_THREAD_COUNT);
                parseOutputForPattern(oa.stderrAsLines(), throwMsg, MULTI_THREAD_COUNT);
                break;

            default: throw new Error("Unknown arg: " + args[0]);
        }
        oa.reportDiagnosticSummary();
    }

    static final int MULTI_THREAD_COUNT = 5;

    static final String stillLocked = "VirtualThread \\(tid:.*exiting with Objects still locked by JNI MonitorEnter";
    static final String terminated = "JavaThread exiting \\(name: \"pool-1-thread-1\"";

    static final String throwMsg = "Terminating via exception as requested";

    private static void parseOutputForPattern(List<String> lines, String pattern, int expected) {
        Pattern p = Pattern.compile(pattern);
        int found = 0;
        for (String line : lines) {
            Matcher m = p.matcher(line);
            if (m.find()) {
                found++;
            }
        }
        if (found != expected) {
            throw new RuntimeException("Checking for pattern \"" + pattern + "\": expected "
                                       + expected + " but found " + found);
        }
    }


    static native int monitorEnter(Object o);
    static native int monitorExit(Object o);

    static class TestBase {

        static {
            System.loadLibrary("JNIMonitor");
        }

        private static Thread.Builder.OfVirtual virtualThreadBuilder(Executor scheduler) {
            Thread.Builder.OfVirtual builder = Thread.ofVirtual();
            try {
                Class<?> clazz = Class.forName("java.lang.ThreadBuilders$VirtualThreadBuilder");
                Constructor<?> ctor = clazz.getDeclaredConstructor(Executor.class);
                ctor.setAccessible(true);
                return (Thread.Builder.OfVirtual) ctor.newInstance(scheduler);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause instanceof RuntimeException re) {
                    throw re;
                }
                throw new RuntimeException(e);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        static void runTest(int nThreads, boolean skipUnlock, boolean throwOnExit) throws Throwable {
            final Object monitor = new Object();
            final AtomicReference<Throwable> exception = new AtomicReference();
            ExecutorService scheduler = Executors.newSingleThreadExecutor();
            ThreadFactory factory = virtualThreadBuilder(scheduler).factory();
            for (int i = 0 ; i < nThreads; i++) {
                Thread th = factory.newThread(() -> {
                        try {
                            int res = monitorEnter(monitor);
                            Asserts.assertTrue(res == 0, "monitorEnter should return 0.");
                            Asserts.assertTrue(Thread.holdsLock(monitor), "monitor should be owned");
                            Thread.yield();
                            if (!skipUnlock) {
                                res = monitorExit(monitor);
                                Asserts.assertTrue(res == 0, "monitorExit should return 0.");
                                Asserts.assertFalse(Thread.holdsLock(monitor), "monitor should be unowned");
                            }
                        } catch (Throwable t) {
                            exception.set(t);
                        }
                        if (throwOnExit) {
                            throw new RuntimeException(throwMsg);
                        }
                    });
                th.start();
                th.join();
                if (exception.get() != null) {
                    throw exception.get();
                }
            }
            scheduler.shutdown();
        }
    }


    static class Normal extends TestBase {
        public static void main(String[] args) throws Throwable {
            runTest(1, false, false);
        }
    }

    static class MultiNormal extends TestBase {
        public static void main(String[] args) throws Throwable {
            runTest(MULTI_THREAD_COUNT, false, false);
        }
    }

    static class MissingUnlock extends TestBase  {
        public static void main(String[] args) throws Throwable {
            runTest(1, true, false);
        }
    }

    static class MultiMissingUnlock extends TestBase {
        public static void main(String[] args) throws Throwable {
            runTest(MULTI_THREAD_COUNT, true, false);
        }
    }

    static class MissingUnlockWithThrow extends TestBase {
        public static void main(String[] args) throws Throwable {
            runTest(1, true, true);
        }
    }

    static class MultiMissingUnlockWithThrow extends TestBase {
        public static void main(String[] args) throws Throwable {
            runTest(MULTI_THREAD_COUNT, true, true);
        }
    }

}
