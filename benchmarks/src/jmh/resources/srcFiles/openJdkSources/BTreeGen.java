/*
 * Copyright (c) 2002, 2018, Oracle and/or its affiliates. All rights reserved.
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

package nsk.sysdict.share;

import java.io.*;

/**
 * This tools generates a binary tree of classes.
 * For more details, use:
 * <pre>
 *     java BTreeGen -help
 * </pre>
 */
public class BTreeGen {
    private static final int HEIGHT = 12;  
    private static final int WEIGHT = 200; 

    private static void explain(Object x) {
        System.err.println("# " + x);
    }

    public static void main(String args[]) {
        if (args.length < 1 || args[0].toLowerCase().startsWith("-h")) {
            explain("This tools generates a binary tree of classes:");
            explain("");
            explain("          BTree0");
            explain("          /     \\");
            explain("    BTree0L    BTree0R");
            explain("     /   \\       /   \\");
            explain("   ...   ...   ...   ...");
            explain("");
            explain("Use:");
            explain("    java BTreeGen { $HEIGHT [ $WEIGHT ] | \"default\" }");
            explain("");
            explain("Generates:");
            explain("    BTree.java class having HEIGHT constant.");
            explain("    BTree0.java defining a classes tree.");
            explain("");
            explain("Here:");
            explain("    HEIGHT and WEIGHT must be positive integers.");
            explain("    Default HEIGHT is " + HEIGHT + " ("
                + ((1<<HEIGHT)-1) + " classes in a tree).");
            explain("    Default WEIGHT is " + WEIGHT + ".");
            System.exit(1);
        };
        int height = HEIGHT;
        int weight = WEIGHT;
        if (!args[0].toLowerCase().equals("default")) {
            height = Integer.parseInt(args[0]);
            if (args.length > 1)
                weight = Integer.parseInt(args[1]);
        };
        try {
            doit(height, weight);
        } catch (IOException exception) {
            exception.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static void doit(int height, int weight)
        throws FileNotFoundException
    {
        PrintStream info;
        info = new PrintStream(new FileOutputStream(new File("BTreeInfo.java")));
        info.println("
        info.println("package nsk.sysdict.share;");
        info.println("import nsk.share.*;");
        info.println("public class BTreeInfo {");
        info.println("    public static final int HEIGHT = " + height + ";");
        info.println("    private static final int WEIGHT = " + weight + ";");
        info.println("    public static final String rootName = \"BTree0\";");
        info.println("    public static final Denotation nodesDenotation");
        info.println("         = new TreeNodesDenotation(\"LR\");");
        info.println("}");
        info.close();
        PrintStream btree;
        btree = new PrintStream(new FileOutputStream(new File("BTree.java")));
        btree.println("
        btree.println("package nsk.sysdict.share;");
        new BTreeGen(btree).generate(height,weight);
        btree.close();
    }

    private PrintStream stream;
    private BTreeGen(PrintStream stream) {
        this.stream = stream;
    }
    private void println(String s) {
        stream.println(s);
    }

    private void generate(int height, int weight) {
        if (height < 1)
            throw new IllegalArgumentException("bad height: " + height);
        if (weight < 1)
            throw new IllegalArgumentException("bad weight: " + weight);
        String basename = "BTree0";
        genClass(null,basename,weight);
        gen(basename,height,weight);
    }

    private void gen(String name, int height, int weight) {
        if (height == 1)
            return;
        String nameL = name + "L";
        String nameR = name + "R";
        genClass(name, nameL, weight);
        genClass(name, nameR, weight);
        gen(nameL, height-1, weight);
        gen(nameR, height-1, weight);
    }

    private void genClass(String baseName, String className, int weight) {
        println("class " + className
            + (baseName!=null? " extends " + baseName: "") + " {");
        for (int w=1; w<=weight; w++)
            println("    static long fill_" + className + "_" + w + ";");
        println("}");
    }
}
