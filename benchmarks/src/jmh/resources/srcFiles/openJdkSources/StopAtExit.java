/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @test
 * @bug 8167108 8266130 8283467 8284632 8286830
 * @summary Stress test JVM/TI StopThread() at thread exit.
 * @requires vm.jvmti
 * @run main/othervm/native -agentlib:StopAtExit StopAtExit
 */

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class StopAtExit extends Thread {
    private final static int DEF_TIME_MAX = 30;  
    private final static String PROG_NAME = "StopAtExit";
    private final static int JVMTI_ERROR_THREAD_NOT_ALIVE = 15;

    public CountDownLatch exitSyncObj = new CountDownLatch(1);
    public CountDownLatch startSyncObj = new CountDownLatch(1);

    native static int stopThread(StopAtExit thr, Throwable exception);

    @Override
    public void run() {
        try {
            startSyncObj.countDown();
            try {
                exitSyncObj.await();
            } catch (InterruptedException e) {
                throw new RuntimeException("Unexpected: " + e);
            }
        } catch (ThreadDeath td) {
        } catch (NoClassDefFoundError ncdfe) {
        }
    }

    public static void main(String[] args) {
        int timeMax = 0;
        if (args.length == 0) {
            timeMax = DEF_TIME_MAX;
        } else {
            try {
                timeMax = Integer.parseUnsignedInt(args[0]);
            } catch (NumberFormatException nfe) {
                System.err.println("'" + args[0] + "': invalid timeMax value.");
                usage();
            }
        }
        timeMax /= 2;  

        test(timeMax);

        Thread threadCreator = new Thread() {
            @Override
            public void run() {
                while (true) {
                    Thread dummyThread = new Thread(() -> {});
                    dummyThread.start();
                    try {
                        dummyThread.join();
                    } catch(InterruptedException ie) {
                    }
                }
            }
        };
        threadCreator.setDaemon(true);
        threadCreator.start();
        test(timeMax);
    }

    public static void test(int timeMax) {
        System.out.println("About to execute for " + timeMax + " seconds.");

        long count = 0;
        long start_time = System.currentTimeMillis();
        while (System.currentTimeMillis() < start_time + (timeMax * 1000)) {
            count++;

            int retCode;
            StopAtExit thread = new StopAtExit();
            thread.start();
            try {
                thread.startSyncObj.await();
                thread.exitSyncObj.countDown();
                long inner_count = 0;
                while (true) {
                    inner_count++;

                    Throwable myException;
                    if ((inner_count % 1) == 1) {
                        myException = new RuntimeException();
                    } else {
                        myException = new ThreadDeath();
                    }

                    retCode = stopThread(thread, myException);

                    if (retCode == JVMTI_ERROR_THREAD_NOT_ALIVE) {
                        break;
                    } else if (retCode != 0) {
                        throw new RuntimeException("thread " + thread.getName()
                                                   + ": stopThread() " +
                                                   "retCode=" + retCode +
                                                   ": unexpected value.");
                    }

                    if (!thread.isAlive()) {
                        break;
                    }
                }
            } catch (InterruptedException e) {
                throw new Error("Unexpected: " + e);
            } catch (NoClassDefFoundError ncdfe) {
            }

            try {
                thread.join();
            } catch (InterruptedException e) {
                throw new Error("Unexpected: " + e);
            }
            retCode = stopThread(thread, new ThreadDeath());

            if (retCode != JVMTI_ERROR_THREAD_NOT_ALIVE) {
                throw new RuntimeException("thread " + thread.getName()
                                           + ": stopThread() " +
                                           "retCode=" + retCode +
                                           ": unexpected value; " +
                                           "expected JVMTI_ERROR_THREAD_NOT_ALIVE(" +
                                           JVMTI_ERROR_THREAD_NOT_ALIVE + ").");
            }

        }

        System.out.println("Executed " + count + " loops in " + timeMax +
                           " seconds.");

        String cmd = System.getProperty("sun.java.command");
        if (cmd != null && !cmd.startsWith("com.sun.javatest.regtest.agent.MainWrapper")) {
            System.exit(0);
        }
    }

    public static void usage() {
        System.err.println("Usage: " + PROG_NAME + " [time_max]");
        System.err.println("where:");
        System.err.println("    time_max  max looping time in seconds");
        System.err.println("              (default is " + DEF_TIME_MAX +
                           " seconds)");
        System.exit(1);
    }
}
