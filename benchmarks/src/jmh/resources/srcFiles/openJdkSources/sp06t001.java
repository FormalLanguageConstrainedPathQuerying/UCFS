/*
 * Copyright (c) 2003, 2018, Oracle and/or its affiliates. All rights reserved.
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

package nsk.jvmti.scenarios.sampling.SP06;

import java.io.PrintStream;

import nsk.share.*;
import nsk.share.jvmti.*;

public class sp06t001 extends DebugeeClass {

    static {
        System.loadLibrary("sp06t001");
    }

    public static void main(String argv[]) {
        argv = nsk.share.jvmti.JVMTITest.commonInit(argv);

        System.exit(run(argv, System.out) + Consts.JCK_STATUS_BASE);
    }

    public static int run(String argv[], PrintStream out) {
        return new sp06t001().runIt(argv, out);
    }

    /* =================================================================== */

    public static final int COMPILE_ITERATIONS = 100;

    ArgumentHandler argHandler = null;
    Log log = null;
    long timeout = 0;
    int status = Consts.TEST_PASSED;

    static Object endingMonitor = new Object();

    sp06t001Thread threads[] = null;

    public int runIt(String argv[], PrintStream out) {
        argHandler = new ArgumentHandler(argv);
        log = new Log(out, argHandler);
        timeout = argHandler.getWaitTime() * 60 * 1000; 

        threads = new sp06t001Thread[] {
            new sp06t001ThreadRunning("threadRunning", log),
            new sp06t001ThreadEntering("threadEntering", log),
            new sp06t001ThreadWaiting("threadWaiting", log),
            new sp06t001ThreadSleeping("threadSleeping", log),
            new sp06t001ThreadRunningInterrupted("threadRunningInterrupted", log),
            new sp06t001ThreadRunningNative("threadRunningNative", log)
        };

        log.display("Starting tested threads");
        try {
            synchronized (endingMonitor) {
                for (int i = 0; i < threads.length; i++) {
                    threads[i].start();
                    if (!threads[i].checkReady()) {
                        throw new Failure("Unable to prepare thread #" + i + ": " + threads[i]);
                    }
                    log.display("  thread ready: " + threads[i].getName());
                }

                log.display("Testing sync: threads ready");
                status = checkStatus(status);
            }
        } finally {
            for (int i = 0; i < threads.length; i++) {
                threads[i].letFinish();
            }
        }

        log.display("Finishing tested threads");
        try {
            for (int i = 0; i < threads.length; i++) {
                threads[i].join();
            }
        } catch (InterruptedException e) {
            throw new Failure(e);
        }

        return status;
    }
}

/* =================================================================== */

abstract class sp06t001Thread extends Thread {
    Log log = null;
    volatile boolean threadReady = false;
    volatile boolean shouldFinish = false;

    public sp06t001Thread(String name, Log log) {
        super(name);
        this.log = log;
    }

    public void run() {
        for (int i = 0; i < sp06t001.COMPILE_ITERATIONS; i++) {
            testedMethod(true, i);
        }
        testedMethod(false, 0);
    }

    public abstract void testedMethod(boolean simulate, int i);

    public boolean checkReady() {
        try {
            while (!threadReady) {
                sleep(1000);
            }
        } catch (InterruptedException e) {
            log.complain("Interrupted " + getName() + ": " + e);
            return false;
        }
        return true;
    }

    public void letFinish() {
        shouldFinish = true;
    }
}

/* =================================================================== */

class sp06t001ThreadRunning extends sp06t001Thread {
    public sp06t001ThreadRunning(String name, Log log) {
        super(name, log);
    }

    public void testedMethod(boolean simulate, int i) {
        if (!simulate) {
            threadReady = true;
            int k = 0;
            int n = 1000;
            while (!shouldFinish) {
                if (n <= 0) {
                    n = 1000;
                }
                if (k > n) {
                    k = 0;
                    n = n - 1;
                }
                k = k + 1;
            }
        }
    }
}

class sp06t001ThreadEntering extends sp06t001Thread {
    public sp06t001ThreadEntering(String name, Log log) {
        super(name, log);
    }

    public void testedMethod(boolean simulated, int i) {
        if (!simulated) {
            threadReady = true;
            synchronized (sp06t001.endingMonitor) {
            }
        }
    }
}

class sp06t001ThreadWaiting extends sp06t001Thread {
    private Object waitingMonitor = new Object();

    public sp06t001ThreadWaiting(String name, Log log) {
        super(name, log);
    }

    public void testedMethod(boolean simulate, int i) {
        synchronized (waitingMonitor) {
            if (!simulate) {
                try {
                    waitingMonitor.wait();
                } catch (InterruptedException ignore) {
                }
            }
        }
    }

    public boolean checkReady() {
        synchronized (waitingMonitor) {
        }
        return true;
    }

    public void letFinish() {
        synchronized (waitingMonitor) {
            waitingMonitor.notifyAll();
        }
    }
}

class sp06t001ThreadSleeping extends sp06t001Thread {
    public sp06t001ThreadSleeping(String name, Log log) {
        super(name, log);
    }

    public void testedMethod(boolean simulate, int i) {
        long longTimeout = 7 * 24 * 60 * 60 * 1000; 

        if (!simulate) {
            try {
                threadReady = true;
                sleep(longTimeout);
            } catch (InterruptedException ignore) {
            }
        }
    }

    public void letFinish() {
        interrupt();
    }
}

class sp06t001ThreadRunningInterrupted extends sp06t001Thread {
    private Object waitingMonitor = new Object();
    volatile boolean interruptReady = false;

    public sp06t001ThreadRunningInterrupted(String name, Log log) {
        super(name, log);
    }

    public void testedMethod(boolean simulate, int i) {
        if (!simulate) {
            interruptReady = true;
            synchronized (waitingMonitor) {
                try {
                    waitingMonitor.wait();
                } catch (InterruptedException ignore) {
                }
            }

            threadReady = true;
            int k = 0;
            int n = 1000;
            while (!shouldFinish) {
                if (n <= 0) {
                    n = 1000;
                }
                if (k > n) {
                    k = 0;
                    n = n - 1;
                }
                k = k + 1;
            }
        }
    }

    public boolean checkReady() {
        while (!interruptReady) {
            try {
                sleep(1000);
            } catch (InterruptedException ie) {
            }
        }
        synchronized (waitingMonitor) {
            interrupt();
        }
        return true;
    }
}

class sp06t001ThreadRunningNative extends sp06t001Thread {
    public sp06t001ThreadRunningNative(String name, Log log) {
        super(name, log);
    }

    public native void testedMethod(boolean simulate, int i);

    public native boolean checkReady();
    public native void letFinish();
}
