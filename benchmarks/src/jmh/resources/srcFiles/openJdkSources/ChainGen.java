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
 * This tools generates a chain of classes.
 * For more details, use:
 * <pre>
 *     java ChainGen -help
 * </pre>
 */
public class ChainGen {
    private static final int FATS_HEIGHT = 5;
    private static final int FATS_WEIGHT = 10000;
    private static final int LEANS_HEIGHT = 50;
    private static final int LEANS_WEIGHT = 1;

    private static void explain(Object x) {
        System.err.println("# " + x);
    }

    public static void main(String args[]) {
        if (args.length < 1 || args[0].toLowerCase().startsWith("-h")) {
            explain("Generates a chain classes extending each other.");
            explain("");
            explain("Use:");
            explain("    java ChainGen $NAME $HEIGHT $WEIGHT");
            explain("Or:");
            explain("    java ChainGen \"fats\"");
            explain("Or:");
            explain("    java ChainGen \"leans\"");
            explain("");
            explain("This creates:");
            explain("    ${NAME}Info.java class displaying HEIGHT and WEIGHT.");
            explain("    ${NAME}XXXXXX.java defining classes chain.");
            explain("");
            explain("Here:");
            explain("    HEIGHT and WEIGHT must be positive integers.");
            explain("    Defaults for \"fats\": HEIGHT is " + FATS_HEIGHT
                + ", WEIGHT is " + FATS_WEIGHT +".");
            explain("    Defaults for \"leans\": HEIGHT is " + LEANS_HEIGHT
                + ", WEIGHT is " + LEANS_WEIGHT +".");
            System.exit(1);
        };
        String name;
        int height, weight;
        if (args[0].toLowerCase().equals("fats")) {
            name   = "Fats";
            height = FATS_HEIGHT;
            weight = FATS_WEIGHT;
        } else if (args[0].toLowerCase().equals("leans")) {
            name   = "Leans";
            height = LEANS_HEIGHT;
            weight = LEANS_WEIGHT;
        } else {
            name   = args[0];
            height = Integer.parseInt(args[1]);
            weight = Integer.parseInt(args[2]);
        };
        try {
            doit(name, height, weight);
        } catch (IOException exception) {
            exception.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static void doit(String name, int height, int weight)
        throws FileNotFoundException
    {
        PrintStream info = new PrintStream(
                new FileOutputStream(new File(name + "Info.java"))
            );
        info.println("
        info.println("package nsk.sysdict.share;");
        info.println("public class " + name + "Info {");
        info.println("    public static final int HEIGHT = " + height + ";");
        info.println("    private static final int WEIGHT = " + weight + ";");
        info.println("    public static final String rootName = \"" + name + "\";");
        info.println("    public static final String[] nodeNames = new String[] {");
        for (int index=0; index<height; index++) {
            String suffix = digits(index,6);
            String className = name + suffix;
            info.println("        \"" + suffix + "\""
                + (index+1<height? "," : ""));
            PrintStream chain = new PrintStream(
                new FileOutputStream(new File(className + ".java"))
            );
            chain.println("
            chain.println("package nsk.sysdict.share;");
            chain.println("class " + className
                + (index>0? " extends " + name + digits(index-1,6): "")
                + " {");
            for (int w=0; w<weight; w++) {
                String fieldName = "fill_" + className + "_" + digits(w,6);
                chain.println("    static long " + fieldName + ";");
            }
            chain.println("}");
            chain.close();
        };
        info.println("    };");
        info.println("}");
        info.close();
    }

    /**
     * Convert <tt>x</tt> to <tt>n</tt>-digits string.
     */
    private static String digits(int x, int n) {
        String s = "" + x;
        while (s.length() < n)
            s = "0" + s;
        return s;
    }
}
