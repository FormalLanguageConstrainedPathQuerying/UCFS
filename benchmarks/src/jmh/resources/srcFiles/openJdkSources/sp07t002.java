/*
 * Copyright (c) 2004, 2018, Oracle and/or its affiliates. All rights reserved.
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

package nsk.jvmti.scenarios.sampling.SP07;

import java.io.PrintStream;

import nsk.share.*;
import nsk.share.jvmti.*;

public class sp07t002 extends DebugeeClass {

    public static void main(String argv[]) {
        argv = nsk.share.jvmti.JVMTITest.commonInit(argv);

        System.exit(run(argv, System.out) + Consts.JCK_STATUS_BASE);
    }

    public static int run(String argv[], PrintStream out) {
        return new sp07t002().runIt(argv, out);
    }

    /* =================================================================== */

    ArgumentHandler argHandler = null;
    Log log = null;
    int status = Consts.TEST_PASSED;
    long timeout = 0;

    sp07t002Thread thread = null;

    public int runIt(String argv[], PrintStream out) {
        argHandler = new ArgumentHandler(argv);
        log = new Log(out, argHandler);
        timeout = argHandler.getWaitTime() * 60 * 1000;
        log.display("Timeout = " + timeout + " msc.");

        thread = new sp07t002Thread("Debuggee Thread");
        thread.start();
        thread.startingBarrier.waitFor();
        status = checkStatus(status);
        thread.letItGo();

        try {
            thread.join(timeout);
        } catch (InterruptedException e) {
            throw new Failure(e);
        }

        log.display("Debugee finished");

        return status;
    }
}

/* =================================================================== */

class sp07t002Thread extends Thread {
    final static int MAX_LADDER = 256;
    public Wicket startingBarrier = new Wicket();
    private volatile boolean flag = true;
    public volatile int depth = -1;

    public sp07t002Thread(String name) {
        super(name);
    }

    public void run() {
        startingBarrier.unlock();

        for (int i = 0; flag; i = (i + 1) % MAX_LADDER) {
            depth = i;
            catcher(i, MAX_LADDER - i);
        }
    }

    void catcher(int n, int m) {
        if (n > 0) {
            catcher(n - 1, m);
        } else {
            try {
                thrower(m);
            } catch (Exception e) {
            }
        }
    }

    void thrower(int n) throws Exception {
        if (n == 0) {
            throw new Exception();
        } else {
            thrower(n - 1);
        }
    }

    public void letItGo() {
        flag = false;
    }
}
