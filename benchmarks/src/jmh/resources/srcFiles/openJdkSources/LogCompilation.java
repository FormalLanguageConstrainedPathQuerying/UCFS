/*
 * Copyright (c) 2009, 2020, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

package com.sun.hotspot.tools.compiler;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.*;

import org.xml.sax.*;
import org.xml.sax.helpers.*;

/**
 * The LogCompilation tool parses log files generated by HotSpot using the
 * {@code -XX:+LogCompilation} command line flag, and outputs the data
 * collected therein in a nicely formatted way. There are various sorting
 * options available, as well as options that select specific compilation
 * events (such as inlining decisions) for inclusion in the output.
 *
 * The tool is also capable of fixing broken compilation logs as sometimes
 * generated by Java 1.5 JVMs.
 */
public class LogCompilation extends DefaultHandler implements ErrorHandler {

    /**
     * Print usage information and terminate with a given exit code.
     */
    public static void usage(int exitcode) {
        System.out.println("Usage: LogCompilation [ -v ] [ -c ] [ -s ] [ -e | -n ] file1 ...");
        System.out.println("By default, the tool will print the logged compilations ordered by start time.");
        System.out.println("  -c:   clean up malformed 1.5 xml");
        System.out.println("  -i:   print inlining decisions");
        System.out.println("  -S:   print compilation statistics");
        System.out.println("  -U:   print uncommon trap statistics");
        System.out.println("  -t:   print with time stamps");
        System.out.println("  -s:   sort events by start time (default)");
        System.out.println("  -e:   sort events by elapsed time");
        System.out.println("  -n:   sort events by name and start");
        System.out.println("  -z:   sort events by compiled code size");
        System.out.println("  -C:   compare logs (give files to compare on command line)");
        System.out.println("  -d:   do not print compilation IDs");
        System.exit(exitcode);
    }

    /**
     * compare controls how some output is formatted
     */
    public static boolean compare = false;

    /**
     * Process command line arguments, parse log files and trigger desired
     * functionality.
     */
    public static void main(String[] args) throws Exception {
        Comparator<LogEvent> sort = LogParser.sortByStart;
        boolean statistics = false;
        boolean printInlining = false;
        boolean cleanup = false;
        boolean trapHistory = false;
        boolean printTimeStamps = false;
        boolean printID = true;
        int index = 0;

        while (args.length > index) {
            String a = args[index];
            if (a.equals("-e")) {
                sort = LogParser.sortByElapsed;
                index++;
            } else if (a.equals("-n")) {
                sort = LogParser.sortByNameAndStart;
                index++;
            } else if (a.equals("-s")) {
                sort = LogParser.sortByStart;
                index++;
            } else if (a.equals("-z")) {
                sort = LogParser.sortByNMethodSize;
                index++;
            } else if (a.equals("-t")) {
                printTimeStamps = true;
                index++;
            } else if (a.equals("-c")) {
                cleanup = true;
                index++;
            } else if (a.equals("-S")) {
                statistics = true;
                index++;
            } else if (a.equals("-U")) {
                trapHistory = true;
                index++;
            } else if (a.equals("-h")) {
                usage(0);
            } else if (a.equals("-i")) {
                printInlining = true;
                index++;
            } else if (a.equals("-C")) {
                compare = true;
                index++;
            } else if (a.equals("-d")) {
                printID = false;
                index++;
            } else {
                if (a.charAt(0) == '-') {
                    System.out.println("Unknown option '" + a + "', assuming file name.");
                }
                break;
            }
        }

        if (index >= args.length) {
            usage(1);
        }

        if (compare) {
            compareLogs(index, args);
            return;
        }

        while (index < args.length) {
            ArrayList<LogEvent> events = null;
            try {
                events = LogParser.parse(args[index], cleanup);
            } catch (FileNotFoundException fnfe) {
                System.out.println("File not found: " + args[index]);
                System.exit(1);
            }

            Collections.sort(events, sort);

            if (statistics) {
                printStatistics(events, System.out);
            } else if (trapHistory) {
                printTrapHistory(events, System.out);
            } else {
                for (LogEvent c : events) {
                    if (c instanceof NMethod) {
                        continue;
                    }
                    if (printTimeStamps) {
                        System.out.print(c.getStart() + ": ");
                    }
                    if (c instanceof Compilation) {
                        Compilation comp = (Compilation) c;
                        comp.print(System.out, printID, printInlining);
                    } else {
                        c.print(System.out, printID);
                    }
                }
            }
            index++;
        }
    }

    /**
     * Print extensive statistics from parsed log files.
     */
    public static void printStatistics(ArrayList<LogEvent> events, PrintStream out) {
        long cacheSize = 0;
        long maxCacheSize = 0;
        int nmethodsCreated = 0;
        int nmethodsLive = 0;
        int[] attempts = new int[32];
        int maxattempts = 0;

        LinkedHashMap<String, Double> phaseTime = new LinkedHashMap<>(7);
        LinkedHashMap<String, Integer> phaseNodes = new LinkedHashMap<>(7);
        double elapsed = 0;

        for (LogEvent e : events) {
            if (e instanceof Compilation) {
                Compilation c = (Compilation) e;
                c.printShort(out);
                out.printf(" %6.4f\n", c.getElapsedTime());
                attempts[c.getAttempts()]++;
                maxattempts = Math.max(maxattempts,c.getAttempts());
                elapsed += c.getElapsedTime();
                for (Phase phase : c.getPhases()) {
                    Double v = phaseTime.get(phase.getName());
                    if (v == null) {
                        v = Double.valueOf(0.0);
                    }
                    phaseTime.put(phase.getName(), Double.valueOf(v.doubleValue() + phase.getElapsedTime()));

                    Integer v2 = phaseNodes.get(phase.getName());
                    if (v2 == null) {
                        v2 = Integer.valueOf(0);
                    }
                    phaseNodes.put(phase.getName(), Integer.valueOf(v2.intValue() + phase.getNodes()));
                    out.printf("\t%s %6.4f %d %d %d %d\n", phase.getName(), phase.getElapsedTime(), phase.getStartNodes(), phase.getNodes(), phase.getStartLiveNodes(), phase.getAddedLiveNodes());
                }
            } else if (e instanceof MakeNotEntrantEvent) {
                MakeNotEntrantEvent mne = (MakeNotEntrantEvent) e;
                NMethod nm = mne.getNMethod();
                if (mne.isZombie()) {
                    if (nm == null) {
                        System.err.println("zombie make not entrant event without nmethod: " + mne.getId());
                    }
                    cacheSize -= nm.getSize();
                    nmethodsLive--;
                }
            } else if (e instanceof NMethod) {
                nmethodsLive++;
                nmethodsCreated++;
                NMethod nm = (NMethod) e;
                cacheSize += nm.getSize();
                maxCacheSize = Math.max(cacheSize, maxCacheSize);
            }
        }
        out.printf("NMethods: %d created %d live %d bytes (%d peak) in the code cache\n", nmethodsCreated, nmethodsLive, cacheSize, maxCacheSize);
        out.println("Phase times:");
        for (String name : phaseTime.keySet()) {
            Double v = phaseTime.get(name);
            Integer v2 = phaseNodes.get(name);
            out.printf("%20s %6.4f %d\n", name, v.doubleValue(), v2.intValue());
        }
        out.printf("%20s %6.4f\n", "total", elapsed);

        if (maxattempts > 0) {
            out.println("Distribution of regalloc passes:");
            for (int i = 0; i <= maxattempts; i++) {
                out.printf("%2d %8d\n", i, attempts[i]);
            }
        }
    }

    /**
     * Container class for a pair of a method and a bytecode instruction index
     * used by a compiler. This is used in
     * {@linkplain #compareLogs() comparing logs}.
     */
    static class MethodBCIPair {
        public MethodBCIPair(Method m, int b, String c, long l) {
            method = m;
            bci = b;
            compiler = c;
            level = l;
        }

        Method method;
        int bci;
        String compiler;
        long level;

        public boolean equals(Object other) {
            if (!(other instanceof MethodBCIPair)) {
                return false;
            }
            MethodBCIPair otherp = (MethodBCIPair)other;
            assert otherp.compiler != null : "otherp null compiler: " + otherp;
            assert method.getCompiler() != compiler : "Compiler doesnt match";
            return (otherp.bci == bci &&
                    otherp.method.equals(method) &&
                    otherp.compiler.equals(compiler) &&
                    otherp.level == level);
        }

        public int hashCode() {
            return method.hashCode() + bci;
        }

        public String toString() {
            if (bci != -1) {
                return method + "@" + bci + " (" + compiler + ")";
            } else {
                return method + " (" + compiler + "(" + level + "))";
            }
        }
    }

    /**
     * Compare a number of compilation log files. Each of the logs is parsed,
     * and all compilations found therein are written to a sorted file (prefix
     * {@code sorted-}. A summary is written to a new file {@code summary.txt}.
     *
     * @param index the index in the command line arguments at which to start
     *              looking for files to compare.
     * @param args  the command line arguments with which {@link LogCompilation}
     *              was originally invoked.
     *
     * @throws Exception in case any exceptions are thrown in the called
     *         methods.
     */
    @SuppressWarnings("unchecked")
    static void compareLogs(int index, String[] args) throws Exception {
        HashMap<MethodBCIPair,MethodBCIPair> methods = new HashMap<>();
        ArrayList<HashMap<MethodBCIPair,Object>> logs = new ArrayList<>();
        PrintStream[] outs = new PrintStream[args.length - index];
        PrintStream summary = new PrintStream(new FileOutputStream("summary.txt"));
        int o = 0;
        while (index < args.length) {
            String basename = new File(args[index]).getName();
            String outname = "sorted-" + basename;
            System.out.println("Sorting " + basename + " to " + outname);
            outs[o] = new PrintStream(new FileOutputStream(outname));
            o++;
            System.out.println("Parsing " + args[index]);
            ArrayList<LogEvent> events = LogParser.parse(args[index], false);
            HashMap<MethodBCIPair,Object> compiles = new HashMap<>();
            logs.add(compiles);
            for (LogEvent c : events) {
                if (c instanceof Compilation) {
                    Compilation comp = (Compilation) c;
                    assert (comp.getNMethod() != null  || comp.getFailureReason() != null ): "NMethod is null in compare: " + comp;
                    String compiler = comp.getNMethod() != null ? comp.getNMethod().getCompiler() :
                            (comp.getCompiler() != null ? comp.getCompiler() : "");
                    assert compiler != null : "Compiler is null in compare: " + comp;
                    long level = -99;
                    if (comp.getLevel() == 0) {
                        if (comp.getNMethod() != null) {
                            level = comp.getNMethod().getLevel();
                        }
                        if (level == 0) {
                            level = comp.getMethod().getLevel();
                        }
                    } else {
                        level = comp.getLevel();
                    }
                    assert level != -99 || comp.getFailureReason() != null : "Failed Compile";
                    MethodBCIPair key = new MethodBCIPair(comp.getMethod(), comp.getBCI(), compiler, level);
                    MethodBCIPair e = methods.get(key);
                    if (e == null) {
                        methods.put(key, key);
                    } else {
                        key = e;
                    }
                    Object other = compiles.get(key);
                    if (other == null) {
                        compiles.put(key, comp);
                    } else {
                        if (!(other instanceof List)) {
                            List<Object> l = new LinkedList<>();
                            l.add(other);
                            l.add(comp);
                            compiles.put(key, l);
                        } else {
                            List<Object> l = (List<Object>) other;
                            l.add(comp);
                        }
                    }
                }
            }
            index++;
        }

        for (MethodBCIPair pair : methods.keySet()) {
            summary.print(pair + " ");
            int base = -1;
            String first = null;
            boolean mismatch = false;
            boolean different = false;
            String[] output = new String[outs.length];
            o = 0;
            for (HashMap<MethodBCIPair,Object> set : logs) {
                Object e = set.get(pair);
                String thisone = null;
                Compilation lastc = null;
                int n;
                if (e == null) {
                    n = 0;
                } else if (e instanceof Compilation) {
                    n = 1;
                    lastc = (Compilation) e;
                } else {
                    n = ((List<Object>) e).size();
                    lastc = (Compilation) ((List<Object>) e).get(n - 1);
                }
                if (lastc != null) {
                    n = 1;
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    PrintStream ps = new PrintStream(baos);
                    lastc.print(ps, false);
                    ps.close();
                    thisone = new String(baos.toByteArray());
                }
                if (base == -1) {
                    base = n;
                } else if (base != n) {
                    mismatch = true;
                }
                output[o++] = thisone;
                if (thisone != null) {
                    if (first == null) {
                        first = thisone;
                    } else {
                        if (!first.equals(thisone)) {
                            different = true;
                        }
                    }
                }
                if (different) {
                    summary.print(n + "d ");
                } else {
                    summary.print(n + " ");
                }
            }
            if (mismatch) {
                summary.print("mismatch");
            }
            summary.println();
            if (different) {
                for (int i = 0; i < outs.length; i++) {
                    if (output[i] != null) {
                        outs[i].println(output[i]);
                    }
                }
            }
        }
        for (int i = 0; i < outs.length; i++) {
            outs[i].close();
        }
        if (summary != System.out) {
            summary.close();
        }
    }

    /**
     * Print the history of uncommon trap events.
     */
    public static void printTrapHistory(ArrayList<LogEvent> events, PrintStream out) {
        LinkedHashMap<String, ArrayList<LogEvent>> traps = new LinkedHashMap<>();
        HashMap<Integer, Compilation> comps = new HashMap<>();

        for (LogEvent e : events) {
            if (e instanceof NMethod) {
                continue;
            }
            if (e instanceof Compilation) {
                Compilation c = (Compilation) e;
                String name = c.getMethod().getFullName();
                ArrayList<LogEvent> elist = traps.get(name);
                if (elist != null && comps.get(c.getId()) == null) {
                    comps.put(c.getId(), c);
                    elist.add(c);
                }
                continue;
            }
            if (e instanceof BasicLogEvent) {
                BasicLogEvent ble = (BasicLogEvent) e;
                Compilation c = ble.getCompilation();
                if (c == null) {
                    continue;
                }
                String name = c.getMethod().getFullName();
                ArrayList<LogEvent> elist = traps.get(name);
                if (elist == null) {
                    elist = new ArrayList<LogEvent>();
                    traps.put(name, elist);
                }
                int bleId = Integer.parseInt(ble.getId());
                if (comps.get(bleId) == null) {
                    comps.put(bleId, c);
                    double start = c.getStart();
                    int ipoint = 0;
                    while (ipoint < elist.size() && elist.get(ipoint).getStart() < start) {
                        ipoint++;
                    }
                    if (ipoint == elist.size()) {
                        elist.add(c);
                    } else {
                        elist.add(ipoint, c);
                    }
                }
                elist.add(ble);
            }
        }

        for (String c: traps.keySet()) {
            ArrayList<LogEvent> elist = traps.get(c);
            String name = ((Compilation) elist.get(0)).getMethod().getFullName();
            System.out.println(name);
            double start = 0;
            for (LogEvent e: elist) {
                if (start > e.getStart() && e.getStart() != 0) {
                    throw new InternalError("wrong sorting order for traps");
                }
                start = e.getStart();
                out.print(e.getStart() + ": ");
                if (e instanceof Compilation) {
                    ((Compilation) e).print(out, true, true, true);
                } else {
                    e.print(out, true);
                }
            }
            out.println();
        }
    }

}
